#!/usr/bin/env python3
"""Tests for build_knowledge.py.  Run: python android-app/tools/test_build_knowledge.py"""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import build_knowledge as bk  # noqa: E402

# The two real schemas, trimmed to a couple of rows each.
SCHEMA_A = (
    "ID,Category,Subcategory,Topic,Content,Keywords\r\n"
    "1,Medical,Combat Trauma,Tourniquet Application,\"Apply 2-3 inches above the wound.\",tourniquet\r\n"
    "2,Weapons,,INSAS rifle,\"The INSAS is a 5.56x45 mm rifle.\",rifle\r\n"
)

SCHEMA_B = (
    "category,topic,content,source\r\n"
    "General,Officer ranks,\"Lieutenant, Captain, Major.\",Indian Army website\r\n"
)


def write(dirpath: Path, name: str, text: str) -> Path:
    path = dirpath / name
    path.write_text(text, encoding="utf-8")
    return path


class HeaderTests(unittest.TestCase):
    def test_normalize_header(self):
        self.assertEqual(bk.normalize_header("Category"), "category")
        self.assertEqual(bk.normalize_header("\ufeffContent "), "content")
        self.assertEqual(bk.normalize_header("Sub-Category"), "sub_category")
        self.assertEqual(bk.normalize_header("S.No"), "s_no")

    def test_resolve_both_schemas(self):
        fields_a = bk.resolve_fields(["ID", "Category", "Subcategory", "Topic", "Content", "Keywords"])
        # Original spelling is preserved for DictReader lookups.
        self.assertEqual(fields_a["topic"], "Topic")
        self.assertEqual(fields_a["subcategory"], "Subcategory")
        self.assertNotIn("source", fields_a)

        fields_b = bk.resolve_fields(["category", "topic", "content", "source"])
        self.assertEqual(fields_b["source"], "source")
        self.assertNotIn("subcategory", fields_b)


class ChunkingTests(unittest.TestCase):
    def test_short_text_is_one_chunk(self):
        self.assertEqual(bk.split_text("Short fact."), ["Short fact."])

    def test_empty_text(self):
        self.assertEqual(bk.split_text("   "), [])

    def test_long_text_respects_ceiling_and_overlap(self):
        sentences = [f"Sentence number {i} about field craft and survival." for i in range(60)]
        chunks = bk.split_text(" ".join(sentences))

        self.assertGreater(len(chunks), 1)
        for chunk in chunks:
            self.assertLessEqual(len(chunk), bk.MAX_CHARS)
            self.assertGreater(len(chunk), 0)
        # Consecutive chunks share some text so a fact spanning the split is
        # still findable in either chunk.
        tail = bk.tail_for_overlap(chunks[0])
        self.assertIn(tail.split(" ")[-2], chunks[1])

    def test_unpunctuated_text_is_hard_sliced(self):
        text = "word " * 1000  # 5000 chars, no sentence breaks
        chunks = bk.split_text(text)
        self.assertGreater(len(chunks), 1)
        for chunk in chunks:
            self.assertLessEqual(len(chunk), bk.MAX_CHARS)

    def test_overlap_never_exceeds_ceiling(self):
        # A long sentence right after a chunk must not push the next one over.
        text = ("a" * 100 + ". ") * 12 + ("b" * 1100) + "."
        for chunk in bk.split_text(text):
            self.assertLessEqual(len(chunk), bk.MAX_CHARS)


class BuildTests(unittest.TestCase):
    def test_merges_mixed_schemas(self):
        with tempfile.TemporaryDirectory() as tmp:
            data_dir = Path(tmp)
            write(data_dir, "a.csv", SCHEMA_A)
            write(data_dir, "b.csv", SCHEMA_B)

            chunks, used, warnings = bk.build_chunks(data_dir)
            self.assertEqual(warnings, [])
            self.assertEqual(used, ["a.csv", "b.csv"])
            self.assertEqual(len(chunks), 3)

            by_topic = {c["topic"]: c for c in chunks}
            self.assertEqual(by_topic["INSAS rifle"]["category"], "Weapons")
            self.assertEqual(by_topic["INSAS rifle"]["source"], "a.csv row 2")
            # Subcategory is optional and simply absent when the CSV lacks it.
            self.assertNotIn("subcategory", by_topic["INSAS rifle"])
            self.assertEqual(by_topic["Tourniquet Application"]["subcategory"], "Combat Trauma")
            self.assertEqual(by_topic["Officer ranks"]["source"], "Indian Army website")

    def test_skips_rows_without_content_and_csv_without_columns(self):
        with tempfile.TemporaryDirectory() as tmp:
            data_dir = Path(tmp)
            write(data_dir, "a.csv", "category,topic,content\nGeneral,Empty,\n")
            write(data_dir, "b.csv", "foo,bar\n1,2\n")
            chunks, used, warnings = bk.build_chunks(data_dir)
            self.assertEqual(chunks, [])
            self.assertEqual(used, [])
            self.assertTrue(any("topic/content" in w for w in warnings))

    def test_duplicate_content_across_files_kept_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            data_dir = Path(tmp)
            write(data_dir, "a.csv", SCHEMA_B)
            write(data_dir, "b.csv", SCHEMA_B)
            chunks, _, _ = bk.build_chunks(data_dir)
            self.assertEqual(len(chunks), 1)

    def test_main_writes_valid_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            data_dir = Path(tmp) / "data"
            data_dir.mkdir()
            write(data_dir, "a.csv", SCHEMA_A)
            out = Path(tmp) / "assets" / "knowledge.json"

            self.assertEqual(bk.main(["--data-dir", str(data_dir), "--out", str(out)]), 0)
            payload = json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual(payload["version"], 1)
            self.assertEqual(payload["chunkCount"], len(payload["chunks"]))
            self.assertIn("a.csv", payload["generatedFrom"])
            for key in ("id", "category", "topic", "source", "text"):
                self.assertIn(key, payload["chunks"][0])

    def test_main_fails_on_missing_dir(self):
        with tempfile.TemporaryDirectory() as tmp:
            missing = Path(tmp) / "nope"
            self.assertEqual(bk.main(["--data-dir", str(missing), "--out", str(Path(tmp) / "x.json")]), 1)


class RealDataTests(unittest.TestCase):
    """Guard the asset that actually ships in the APK."""

    def test_real_data_yields_chunks(self):
        chunks, used, _ = bk.build_chunks(bk.DEFAULT_DATA_DIR)
        self.assertGreater(len(used), 0)
        self.assertGreater(len(chunks), 200)
        for chunk in chunks:
            self.assertTrue(chunk["topic"])
            self.assertTrue(chunk["text"])
            self.assertLessEqual(len(chunk["text"]), bk.MAX_CHARS)


if __name__ == "__main__":
    unittest.main(verbosity=2)
