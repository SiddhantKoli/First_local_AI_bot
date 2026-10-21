# 🇮🇳 Sainik Sahayak (सैनिक सहायक)

**AI Assistant for Indian Army Personnel** — powered by Llama 3.2 + ChromaDB RAG

A local, offline-capable chatbot that provides instant answers on Indian military topics using Retrieval-Augmented Generation (RAG).

## 📚 Knowledge Base Coverage

| Category | Topics |
|---|---|
| **Medical** | Combat trauma, high altitude medicine, infectious disease, first aid, mental health, nutrition |
| **Weapons** | INSAS, AK-203, SIG716, Dragunov SVD, NEGEV LMG, Carl Gustaf, T-90, Arjun, BMP-2, artillery, BrahMos, weapon care |
| **Field Craft** | Map reading, compass, GPS/NAVIC, camouflage, patrolling, survival, water purification |
| **Tactical** | Counter-insurgency, IED, urban warfare, mountain warfare, NBC/CBRN |
| **Technical** | Radio comms, BMP-2, T-90, night vision, OPSEC |
| **Administrative** | Army Act, courts martial, promotions, welfare, ROE, casualty procedures |
| **Food** | Ration scales, high-altitude nutrition, DFRL ready-to-eat meals, field hygiene, hydration |
| **Etiquette** | Saluting, addressing officers, chain of command, war cries, mess customs, uniform, social media security |
| **General** | Battle honours, rank structure, organization, India's borders |

## 🚀 Quick Start

### Prerequisites
- Python 3.11+
- [Ollama](https://ollama.ai) installed and running
- Models pulled:
  ```bash
  ollama pull llama3.2            # answer generation
  ollama pull mxbai-embed-large   # embeddings for ChromaDB
  ```

Everything runs on your machine: the LLM, embeddings, vector store and the web UI
assets (fonts, icons, Markdown renderer) are all local, so no internet connection is
needed once the models are pulled.

### Setup

```bash
# 1. Activate virtual environment
.venv\Scripts\activate       # Windows

# 2. Install dependencies
pip install -r requirements.txt

# 3. Ingest knowledge base into ChromaDB
python ingest.py

# 4a. Run the web app (recommended)
python -m uvicorn api:app --host 127.0.0.1 --port 8000
#     then open http://127.0.0.1:8000

# 4b. Or run the CLI chatbot
python main.py

# 4c. Or run the Streamlit UI
streamlit run app.py
```

> `api.py` serves both the JSON API (`POST /api/chat`, `POST /api/reset`) and the
> static frontend in `static/`. Running `python api.py` instead starts the same app
> on port 8443 over HTTPS using the self-signed `cert.pem`/`key.pem`.

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
│ ChromaDB        │◄────│ 236 Knowledge│
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
│   ├── indian_military_rag_knowledge_base.csv      # Core knowledge base (60 entries)
│   ├── indian_military_rag_knowledge_base_v2.csv   # Extended knowledge base (116 entries)
│   ├── more_knowledge.csv                          # Weapons, food & etiquette (31 entries)
│   └── starter_knowledge.csv                       # Ranks, field craft & first aid (29 entries)
├── chroma_db/                                    # Vector store (auto-generated)
├── chatbot.py                                    # RAG engine core
├── ingest.py                                     # CSV → ChromaDB ingestion
├── main.py                                       # CLI interface
├── api.py                                        # FastAPI server + static frontend
├── static/                                       # Web UI (fully offline assets)
├── requirements.txt                              # Python dependencies
└── README.md
```

## ⚙️ Tech Stack

- **LLM**: Llama 3.2 via Ollama (fully local, no API keys)
- **Vector DB**: ChromaDB (persistent local storage)
- **Framework**: LangChain (RAG pipeline orchestration)
- **Embeddings**: `mxbai-embed-large` via Ollama (Ollama no longer serves embeddings
  from the Llama 3.2 chat model, so a dedicated embedding model is required)

## ➕ Adding Knowledge

Drop any `.csv` into `data/` and re-run `python ingest.py`. Columns are matched by
header name, so both schemas work and order does not matter:

```csv
ID,Category,Subcategory,Topic,Content,Keywords
```

```csv
category,topic,content,source
```

`Subcategory` and `Keywords` are optional. Duplicate entries (same topic + content)
are skipped automatically, and the vector store is rebuilt from scratch on every run.
