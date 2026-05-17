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
EMBEDDING_MODEL = "llama3.2"


def load_csv_to_documents(csv_path: str) -> list[Document]:
    """Load the military knowledge base CSV format."""
    documents = []
    try:
        with open(csv_path, "r", encoding="utf-8") as f:
            reader = csv.reader(f)
            header = next(reader)

            for row in reader:
                if len(row) < 5:
                    continue

                row_id = row[0].strip()
                category = row[1].strip()
                subcategory = row[2].strip()
                topic = row[3].strip()
                content = row[4].strip()
                keywords = ",".join(row[5:]).strip() if len(row) > 5 else ""

                if not content:
                    continue

                content_parts = []
                if category: content_parts.append(f"Category: {category}")
                if subcategory: content_parts.append(f"Subcategory: {subcategory}")
                if topic: content_parts.append(f"Topic: {topic}")
                content_parts.append(f"\n{content}")

                full_content = "\n".join(content_parts)
                metadata = {"source": csv_path, "topic": topic, "category": category}

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
    print("[DONE] Run the web app with: streamlit run app.py")
