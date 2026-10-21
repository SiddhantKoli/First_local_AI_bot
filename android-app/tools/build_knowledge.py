#!/usr/bin/env python3
"""
Build android-app/app/src/main/assets/knowledge.json from the project's CSVs.

The desktop app ingests the CSVs into ChromaDB with an embedding model. A phone
cannot run that pipeline on first launch, so we bake the same knowledge into the
APK as plain JSON and search it on-device with BM25 (see Bm25Retriever.kt).

The CSVs in data/ do NOT all share a schema:

  indian_military_rag_knowledge_base*.csv -> ID, Category, Subcategory, Topic, Content, Keywords
  more_knowledge.csv / starter_knowledge.csv -> category, topic, content, source

So headers are normalised (case, BOM, punctuation) and looked up through alias
lists. Missing columns are tolerated: `source` falls back to a file/row reference
and `subcategory` is simply absent.

Long `content` values are split into ~1000 character chunks with a small overlap,
so that a retrieved chunk stays small enough to fit several of them in the
on-device model's context window without cutting a fact in half.

Usage:
    python android-app/tools/build_knowledge.py
    python android-app/tools/build_knowledge.py --data-dir data --out /tmp/k.json
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
from pathlib import Path

APP_ROOT = Path(__file__).resolve().parents[1]          # android-app/
REPO_ROOT = APP_ROOT.parent                             # repo root
DEFAULT_DATA_DIR = REPO_ROOT / "data"
DEFAULT_OUT = APP_ROOT / "app" / "src" / "main" / "assets" / "knowledge.json"

TARGET_CHARS = 1000   # aim for chunks around this size
MAX_CHARS = 1200      # hard ceiling for one chunk
OVERLAP_CHARS = 150   # repeated tail so a fact split across chunks stays searchable

# Field -> accepted header spellings (after normalisation). Order matters only
# for readability; every field is optional except topic + content.
FIELD_ALIASES = {
    "id": {"id", "row_id", "s_no", "sr_no", "no"},
    "category": {"category", "category_name", "section"},
    "subcategory": {"subcategory", "sub_category", "subsection"},
    "topic": {"topic", "title", "heading", "name"},
    "content": {"content", "text", "body", "answer", "description"},
    "source": {"source", "reference", "citation", "url"},
    "keywords": {"keywords", "tags"},
}


def normalize_header(name: str) -> str:
    """' Sub-Category ' / '\\ufeffCategory' -> 'sub_category' / 'category'."""
    cleaned = (name or "").lstrip("\ufeff").strip().lower()
    return re.sub(r"[^a-z0-9]+", "_", cleaned).strip("_")


def resolve_fields(headers: list[str]) -> dict[str, str]:
    """Map our field names onto the real header spellings of this CSV.

    The original spelling is kept (not the normalised one) because DictReader
    keys rows by the header exactly as it appears in the file.
    """
    resolved: dict[str, str] = {}
    for field, aliases in FIELD_ALIASES.items():
        for header in headers:
            if header is not None and normalize_header(header) in aliases:
                resolved[field] = header
                break
    return resolved


def read_rows(path: Path) -> tuple[list[dict[str, str]], list[str]]:
    """Read one CSV, returning (normalised rows, warnings).

    utf-8-sig strips the BOM that Excel happily writes; newline="" is required by
    the csv module for embedded newlines inside quoted fields.
    """
    warnings: list[str] = []
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        headers = reader.fieldnames or []
        fields = resolve_fields(headers)

        if "topic" not in fields or "content" not in fields:
            warnings.append(f"{path.name}: no topic/content columns (headers={headers}) - skipped")
            return [], warnings

        rows: list[dict[str, str]] = []
        for line_no, raw in enumerate(reader, start=2):
            # Re-key by our field names so downstream code is schema-agnostic.
            row = {field: (raw.get(header) or "").strip() for field, header in fields.items()}
            row.setdefault("id", "")
            row["_line"] = str(line_no)
            rows.append(row)
        return rows, warnings


def tail_for_overlap(text: str, overlap: int = OVERLAP_CHARS) -> str:
    """Last <=overlap characters of text, starting at a word boundary."""
    if len(text) <= overlap:
        return text
    tail = text[-overlap:]
    cut = tail.find(" ")
    return tail[cut + 1:] if cut != -1 else tail


def split_text(
    text: str,
    target: int = TARGET_CHARS,
    maximum: int = MAX_CHARS,
    overlap: int = OVERLAP_CHARS,
) -> list[str]:
    """Split text on sentence boundaries into chunks of roughly `target` chars.

    Falls back to hard slicing for a single sentence longer than `maximum` (some
    CSV rows are one long paragraph without punctuation).
    """
    text = re.sub(r"\s+", " ", text or "").strip()
    if not text:
        return []
    if len(text) <= maximum:
        return [text]

    sentences = re.split(r"(?<=[.!?;:])\s+", text)
    chunks: list[str] = []
    current = ""

    for sentence in sentences:
        # A single very long sentence: emit it in hard slices.
        while len(sentence) > maximum:
            if current:
                chunks.append(current)
                current = ""
            chunks.append(sentence[:maximum])
            sentence = sentence[maximum - overlap:]

        if not current:
            current = sentence
        elif len(current) + 1 + len(sentence) <= target:
            current = f"{current} {sentence}"
        else:
            chunks.append(current)
            tail = tail_for_overlap(current, overlap)
            # Only carry the overlap when it still leaves the next chunk under
            # `maximum`; otherwise start clean.
            if tail and len(tail) + 1 + len(sentence) <= maximum:
                current = f"{tail} {sentence}"
            else:
                current = sentence

    if current:
        chunks.append(current)
    return chunks


def slugify(*parts: str) -> str:
    joined = "-".join(p for p in parts if p)
    return re.sub(r"-{2,}", "-", re.sub(r"[^a-z0-9]+", "-", joined.lower())).strip("-")


def build_chunks(data_dir: Path) -> tuple[list[dict], list[str], list[str]]:
    """Merge every CSV in data_dir into a flat list of chunk records."""
    warnings: list[str] = []
    used_files: list[str] = []
    chunks: list[dict] = []
    seen_content: set[str] = set()
    seen_ids: dict[str, int] = {}

    for path in sorted(data_dir.glob("*.csv")):
        rows, file_warnings = read_rows(path)
        warnings.extend(file_warnings)
        if not rows:
            continue

        produced = 0
        for row in rows:
            content = row.get("content", "")
            topic = row.get("topic", "")
            if not content or not topic:
                continue  # nothing to search or cite

            # Identical rows appear in more than one file; keep the first.
            fingerprint = hashlib.sha1(re.sub(r"\s+", " ", content).lower().encode()).hexdigest()
            if fingerprint in seen_content:
                continue
            seen_content.add(fingerprint)

            category = row.get("category") or "General"
            # A source column is not guaranteed (the v1/v2 files have none).
            source = row.get("source") or f"{path.name} row {row.get('id') or row['_line']}"

            base_id = slugify(category, topic)
            pieces = split_text(content)
            for index, piece in enumerate(pieces):
                # Stable, unique id: same CSV always produces the same ids, which
                # keeps the generated asset diffable in git.
                count = seen_ids.get(base_id, 0)
                seen_ids[base_id] = count + 1
                chunk_id = base_id if len(pieces) == 1 and count == 0 else f"{base_id}-{count}"

                chunk = {
                    "id": chunk_id,
                    "category": category,
                    "topic": topic,
                    "source": source,
                    "text": piece,
                }
                if row.get("subcategory"):
                    chunk["subcategory"] = row["subcategory"]
                if len(pieces) > 1:
                    chunk["part"] = f"{index + 1}/{len(pieces)}"
                chunks.append(chunk)
                produced += 1

        if produced:
            used_files.append(path.name)

    return chunks, used_files, warnings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA_DIR)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)

    if not args.data_dir.is_dir():
        print(f"error: data directory not found: {args.data_dir}", file=sys.stderr)
        return 1

    chunks, used_files, warnings = build_chunks(args.data_dir)
    for warning in warnings:
        print(f"warning: {warning}", file=sys.stderr)
    if not chunks:
        print(f"error: no usable rows found in {args.data_dir}", file=sys.stderr)
        return 1

    payload = {
        "version": 1,
        "generatedFrom": used_files,
        "chunkCount": len(chunks),
        "chunks": chunks,
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    # separators= keeps the asset small; sort_keys makes diffs stable.
    args.out.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True),
        encoding="utf-8",
    )

    size_kb = args.out.stat().st_size / 1024
    print(f"wrote {len(chunks)} chunks from {len(used_files)} CSV(s) -> {args.out} ({size_kb:.0f} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
