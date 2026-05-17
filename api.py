import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from chatbot import MilitaryChatbot
from contextlib import asynccontextmanager

# Define the request model
class ChatRequest(BaseModel):
    prompt: str

# Create a global instance of the chatbot
bot = MilitaryChatbot()

# Lifespan context manager for startup/shutdown
@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize bot on startup
    print("Starting FastAPI Server... Initializing Chatbot")
    bot.initialize()
    yield
    print("Shutting down server...")

app = FastAPI(lifespan=lifespan)

# API Endpoint for chat
@app.post("/api/chat")
async def chat(request: ChatRequest):
    try:
        response = bot.ask(request.prompt)
        return {"success": True, "data": response}
    except Exception as e:
        return {"success": False, "error": str(e)}

# API Endpoint to reset memory
@app.post("/api/reset")
async def reset():
    bot.reset_memory()
    return {"success": True}

# Serve the static files (HTML, CSS, JS, images)
# Mount it at the root so visiting / opens index.html
app.mount("/", StaticFiles(directory="static", html=True), name="static")

if __name__ == "__main__":
    import uvicorn
    import os
    
    # Check if encryption certificates exist
    if os.path.exists("key.pem") and os.path.exists("cert.pem"):
        print("Starting SECURE E2EE Server (HTTPS)...")
        uvicorn.run("api:app", host="0.0.0.0", port=8443, 
                    ssl_keyfile="key.pem", ssl_certfile="cert.pem")
    else:
        print("Starting UNSECURE Server (HTTP)...")
        uvicorn.run("api:app", host="0.0.0.0", port=8443, reload=True)
