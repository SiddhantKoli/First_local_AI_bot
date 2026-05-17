"""
Indian Military RAG Chatbot -- Core Engine
------------------------------------------
This module implements the Retrieval-Augmented Generation (RAG) chatbot
using LangChain, Llama 3.2 (via Ollama), and ChromaDB.
"""

import sys
import io

from langchain_chroma import Chroma
from langchain_ollama import OllamaEmbeddings
from langchain_ollama import ChatOllama
from langchain_core.prompts import ChatPromptTemplate, PromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_core.runnables import RunnablePassthrough

# --- Configuration ---
CHROMA_DB_DIR = "chroma_db"
COLLECTION_NAME = "indian_military_kb"
LLM_MODEL = "llama3.2"
EMBEDDING_MODEL = "llama3.2"

# --- System Prompt ---
SYSTEM_PROMPT = """You are Sainik Sahayak, an AI assistant specialized in Indian Military knowledge.

Your purpose is to provide accurate, practical information to Indian Army personnel on:
- Medical: Combat trauma, first aid, high altitude medicine, mental health
- Weapons: Rifles (INSAS, AK-203), crew-served weapons, sniper systems
- Field Craft: Navigation, camouflage, patrolling, survival skills
- Tactical: Counter-insurgency, urban warfare, mountain warfare, NBC defense
- Technical: Communications, vehicles (BMP-2, T-90), night vision, signals
- Administrative: Army Act, promotions, welfare schemes, legal matters
- General Knowledge: Indian Army history, ranks, organization, borders

RULES:
1. Answer ONLY based on the context provided. If the information is not in the context, say so clearly.
2. Be precise and actionable -- soldiers depend on this information in the field.
3. When discussing medical procedures, always emphasize seeking qualified medical personnel.
4. Use military terminology appropriate for Indian Army personnel.
5. For safety-critical topics (weapons, NBC, medical), include relevant warnings.
6. Keep answers concise but complete -- field conditions demand clarity.
7. If asked about something outside military scope, politely redirect.

CONTEXT FROM KNOWLEDGE BASE:
{context}

SOLDIER'S QUESTION: {question}

YOUR RESPONSE:"""


def format_docs(docs):
    """Format retrieved documents into a single context string."""
    return "\n\n---\n\n".join(doc.page_content for doc in docs)


class MilitaryChatbot:
    """
    Indian Military RAG Chatbot powered by Llama 3.2 + ChromaDB.
    """

    def __init__(self):
        self.embeddings = None
        self.vectorstore = None
        self.llm = None
        self.chain = None
        self.retriever = None
        self.chat_history = []
        self._initialized = False

    def initialize(self) -> None:
        """
        Initialize all components: embeddings, vector store, LLM, and chain.
        """
        print("[INIT] Initializing Sainik Sahayak...")

        # 1. Setup embeddings
        print("   -> Loading embedding model...")
        self.embeddings = OllamaEmbeddings(model=EMBEDDING_MODEL)

        # 2. Connect to ChromaDB
        print("   -> Connecting to ChromaDB knowledge base...")
        self.vectorstore = Chroma(
            persist_directory=CHROMA_DB_DIR,
            embedding_function=self.embeddings,
            collection_name=COLLECTION_NAME,
        )

        # Verify the collection has documents
        collection_count = self.vectorstore._collection.count()
        if collection_count == 0:
            raise RuntimeError(
                "ChromaDB collection is empty! Run 'python ingest.py' first."
            )
        print(f"   -> Knowledge base loaded: {collection_count} documents")

        # 3. Setup LLM
        print("   -> Loading Llama 3.2 via Ollama...")
        self.llm = ChatOllama(
            model=LLM_MODEL,
            temperature=0.3,  # Low temperature for factual accuracy
        )

        # 4. Setup retriever
        print("   -> Building RAG pipeline...")
        self.retriever = self.vectorstore.as_retriever(
            search_type="mmr",  # Maximal Marginal Relevance for diversity
            search_kwargs={
                "k": 5,         # Return top 5 relevant documents
                "fetch_k": 10,  # Fetch 10 candidates before MMR filtering
            },
        )

        # 5. Build the RAG chain using LCEL (LangChain Expression Language)
        prompt = ChatPromptTemplate.from_template(SYSTEM_PROMPT)

        self.chain = (
            {"context": self.retriever | format_docs, "question": RunnablePassthrough()}
            | prompt
            | self.llm
            | StrOutputParser()
        )

        self._initialized = True
        print("[READY] Sainik Sahayak is ready!\n")

    def ask(self, question: str) -> dict:
        """
        Ask a question and get a response with source references.

        Returns:
            dict with 'answer' and 'sources' keys
        """
        if not self._initialized:
            raise RuntimeError("Chatbot not initialized. Call initialize() first.")

        # Get retrieved docs for source tracking
        retrieved_docs = self.retriever.invoke(question)

        # Get answer from the chain
        answer = self.chain.invoke(question)

        # Extract source information
        sources = []
        for doc in retrieved_docs:
            source_info = {
                "topic": doc.metadata.get("topic", "Unknown"),
                "category": doc.metadata.get("category", "Unknown"),
                "subcategory": doc.metadata.get("subcategory", "Unknown"),
            }
            if source_info not in sources:
                sources.append(source_info)

        # Store in chat history
        self.chat_history.append({"question": question, "answer": answer})

        return {
            "answer": answer,
            "sources": sources,
        }

    def reset_memory(self) -> None:
        """Clear conversation history."""
        self.chat_history.clear()
        print("[INFO] Conversation memory cleared.\n")
