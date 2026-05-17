# 🇮🇳 Sainik Sahayak (सैनिक सहायक)

**AI Assistant for Indian Army Personnel** — powered by Llama 3.2 + ChromaDB RAG

A local, offline-capable chatbot that provides instant answers on Indian military topics using Retrieval-Augmented Generation (RAG).

## 📚 Knowledge Base Coverage

| Category | Topics |
|---|---|
| **Medical** | Combat trauma, high altitude medicine, infectious disease, first aid, mental health, nutrition |
| **Weapons** | INSAS, AK-203, Dragunov SVD, NEGEV LMG, Carl Gustaf, 81mm Mortar |
| **Field Craft** | Map reading, compass, GPS/NAVIC, camouflage, patrolling, survival |
| **Tactical** | Counter-insurgency, IED, urban warfare, mountain warfare, NBC/CBRN |
| **Technical** | Radio comms, BMP-2, T-90, night vision, OPSEC |
| **Administrative** | Army Act, promotions, welfare, ROE, casualty procedures |
| **General** | Battle honours, rank structure, organization, India's borders |

## 🚀 Quick Start

### Prerequisites
- Python 3.11+
- [Ollama](https://ollama.ai) installed and running
- Llama 3.2 model pulled: `ollama pull llama3.2`

### Setup

```bash
# 1. Activate virtual environment
.venv\Scripts\activate       # Windows

# 2. Install dependencies
pip install -r requirements.txt

# 3. Ingest knowledge base into ChromaDB
python ingest.py

# 4. Run the chatbot
python main.py
```

### Chatbot Commands
- `/help` — Show all available topics
- `/clear` — Reset conversation history
- `/quit` — Exit the chatbot

## 🏗️ Architecture

```
User Question
     │
     ▼
┌─────────────────┐
│ Llama 3.2       │ ← Condenses follow-up questions
│ (Question       │
│  Condenser)     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌──────────────┐
│ ChromaDB        │◄────│ 60 Knowledge │
│ Vector Store    │     │ Base Entries │
│ (MMR Retrieval) │     └──────────────┘
└────────┬────────┘
         │ Top 5 relevant docs
         ▼
┌─────────────────┐
│ Llama 3.2       │
│ (Answer         │
│  Generator)     │
└────────┬────────┘
         │
         ▼
   Answer + Sources
```

## 📁 Project Structure

```
Local_AI_Agent/
├── data/
│   └── indian_military_rag_knowledge_base.csv   # Knowledge base (60 entries)
├── chroma_db/                                    # Vector store (auto-generated)
├── chatbot.py                                    # RAG engine core
├── ingest.py                                     # CSV → ChromaDB ingestion
├── main.py                                       # CLI interface
├── requirements.txt                              # Python dependencies
└── README.md
```

## ⚙️ Tech Stack

- **LLM**: Llama 3.2 via Ollama (fully local, no API keys)
- **Vector DB**: ChromaDB (persistent local storage)
- **Framework**: LangChain (RAG pipeline orchestration)
- **Embeddings**: Llama 3.2 embeddings via Ollama
