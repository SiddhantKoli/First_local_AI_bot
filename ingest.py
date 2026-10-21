"""
Indian Military RAG Knowledge Base Ingestion Script
----------------------------------------------------
This script reads all files (CSV, PDF, TXT) from the data/ folder 
and ingests them into a ChromaDB vector store.

Usage:
    python ingest.py
"""

import csv
import sys
import io
import os
import glob

from langchain_chroma import Chroma
from langchain_ollama import OllamaEmbeddings
from langchain_core.documents import Document
from langchain_community.document_loaders import PyPDFLoader, TextLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter

# Fix Windows console encoding
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# --- Configuration ---
DATA_DIR = "data"
CHROMA_DB_DIR = "chroma_db"
COLLECTION_NAME = "indian_military_kb"
EMBEDDING_MODEL = "mxbai-embed-large"


def normalize_header(name: str) -> str:
    """Lower-case a CSV column name and drop BOM/whitespace."""
    return (name or "").strip().lstrip("\ufeff").lower()


def load_csv_to_documents(csv_path: str) -> list[Document]:
    """
    Load a knowledge CSV and map its columns by header name.

    Supported layouts (column order does not matter):
      * ID, Category, Subcategory, Topic, Content, Keywords
      * Category, Topic, Content, Source
    """
    documents = []
    try:
        with open(csv_path, "r", encoding="utf-8-sig", newline="") as f:
            reader = csv.DictReader(f)
            if not reader.fieldnames:
                print(f"[WARNING] Empty CSV (no header): {csv_path}")
                return documents

            # Map normalised header name -> actual column name in the file
            columns = {normalize_header(name): name for name in reader.fieldnames}

            def field(row, *names):
                for name in names:
                    column = columns.get(name)
                    if column is None:
                        continue
                    value = (row.get(column) or "").strip()
                    if value:
                        return value
                return ""

            for row in reader:
                category = field(row, "category")
                subcategory = field(row, "subcategory", "sub_category")
                topic = field(row, "topic", "title")
                content = field(row, "content", "text", "answer")
                keywords = field(row, "keywords", "tags")
                attribution = field(row, "source", "reference", "attribution")

                if not content:
                    continue

                content_parts = []
                if category: content_parts.append(f"Category: {category}")
                if subcategory: content_parts.append(f"Subcategory: {subcategory}")
                if topic: content_parts.append(f"Topic: {topic}")
                content_parts.append(f"\n{content}")

                full_content = "\n".join(content_parts)
                metadata = {
                    "source": csv_path,
                    "topic": topic or "Unknown",
                    "category": category or "General",
                    "subcategory": subcategory,
                    "keywords": keywords,
                }
                if attribution:
                    metadata["attribution"] = attribution

                documents.append(Document(page_content=full_content, metadata=metadata))
    except Exception as e:
        print(f"[ERROR] Failed to load CSV {csv_path}: {e}")
    return documents


def load_pdf_to_documents(pdf_path: str) -> list[Document]:
    """Load and split PDF documents."""
    documents = []
    try:
        loader = PyPDFLoader(pdf_path)
        docs = loader.load()
        
        # Split large PDF pages into smaller chunks
        text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000,
            chunk_overlap=200
        )
        documents = text_splitter.split_documents(docs)
    except Exception as e:
        print(f"[ERROR] Failed to load PDF {pdf_path}: {e}")
    return documents


def load_txt_to_documents(txt_path: str) -> list[Document]:
    """Load and split plain text documents."""
    documents = []
    try:
        loader = TextLoader(txt_path, encoding="utf-8")
        docs = loader.load()
        
        text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000,
            chunk_overlap=200
        )
        documents = text_splitter.split_documents(docs)
    except Exception as e:
        print(f"[ERROR] Failed to load TXT {txt_path}: {e}")
    return documents


def ingest_all_documents():
    """Scan data directory and ingest all supported files."""
    # Clear existing Chroma DB to prevent duplicate entries
    import shutil
    if os.path.exists(CHROMA_DB_DIR):
        print(f"[INFO] Removing old vector store at: {CHROMA_DB_DIR}")
        try:
            shutil.rmtree(CHROMA_DB_DIR)
            print("[INFO] Successfully cleared old vector store.")
        except Exception as e:
            print(f"[WARNING] Could not delete old vector store (may be locked/in use): {e}")

    print(f"[INFO] Scanning '{DATA_DIR}/' for documents...")
    all_documents = []

    # Ensure data dir exists
    if not os.path.exists(DATA_DIR):
        os.makedirs(DATA_DIR)

    for file_path in glob.glob(f"{DATA_DIR}/*"):
        ext = os.path.splitext(file_path)[1].lower()
        
        if ext == '.csv':
            print(f"  -> Loading CSV: {file_path}")
            all_documents.extend(load_csv_to_documents(file_path))
        elif ext == '.pdf':
            print(f"  -> Loading PDF: {file_path}")
            all_documents.extend(load_pdf_to_documents(file_path))
        elif ext in ['.txt', '.md']:
            print(f"  -> Loading Text: {file_path}")
            all_documents.extend(load_txt_to_documents(file_path))

    if not all_documents:
        print("[WARNING] No documents found in data folder. Add some PDFs, CSVs, or TXT files!")
        return

    # Drop exact duplicates so overlapping CSVs don't bias retrieval
    unique_documents = []
    seen = set()
    for doc in all_documents:
        key = (doc.metadata.get("topic"), doc.page_content)
        if key in seen:
            continue
        seen.add(key)
        unique_documents.append(doc)

    duplicates = len(all_documents) - len(unique_documents)
    if duplicates:
        print(f"[INFO] Skipped {duplicates} duplicate entries.")
    all_documents = unique_documents

    print(f"\n[INFO] Loaded a total of {len(all_documents)} document chunks.")
    print(f"[INFO] Connecting to Ollama for embeddings using model: {EMBEDDING_MODEL}")

    embeddings = OllamaEmbeddings(model=EMBEDDING_MODEL)

    print(f"[INFO] Updating ChromaDB vector store at: {CHROMA_DB_DIR}")

    vectorstore = Chroma.from_documents(
        documents=all_documents,
        embedding=embeddings,
        persist_directory=CHROMA_DB_DIR,
        collection_name=COLLECTION_NAME,
    )

    print(f"[SUCCESS] Ingested {len(all_documents)} chunks into ChromaDB!")
    print(f"   Collection: {COLLECTION_NAME}")


if __name__ == "__main__":
    print("=" * 60)
    print("  Indian Military Knowledge Base -- Data Ingestion")
    print("=" * 60)
    print()

    ingest_all_documents()

    print()
    print("[DONE] Run the web app with: uvicorn api:app --port 8000 (or: streamlit run app.py)")
