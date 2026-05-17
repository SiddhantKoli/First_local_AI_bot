"""
Indian Military RAG Chatbot -- Main Entry Point
-------------------------------------------------
Sainik Sahayak -- AI Assistant for Indian Army Personnel

Usage:
    1. First, ingest the knowledge base:  python ingest.py
    2. Then run the chatbot:              python main.py
"""

import sys
import io

from chatbot import MilitaryChatbot


def print_banner():
    """Display the startup banner."""
    banner = """
================================================================
|                                                              |
|          SAINIK SAHAYAK  (Indian Military AI)                 |
|                                                              |
|       AI Assistant for Indian Army Personnel                 |
|       Powered by Llama 3.2 + ChromaDB RAG                   |
|                                                              |
================================================================

  Categories: Medical | Weapons | Field Craft | Tactical
              Technical | Administrative | General Knowledge

  Commands:   /clear  -- Reset conversation history
              /help   -- Show available topics
              /quit   -- Exit the chatbot
"""
    print(banner)


def print_help():
    """Display available knowledge base topics."""
    help_text = """
--- Available Knowledge Base Topics ---

MEDICAL
  * Combat Trauma -- Tourniquet, wound packing, pneumothorax, burns
  * High Altitude -- AMS, HAPE, HACE, hypothermia
  * Infectious Disease -- Heat stroke, malaria, snake bites, scrub typhus
  * First Aid -- CPR, airway management, recovery position
  * Mental Health -- PTSD, combat stress, buddy system
  * Nutrition -- Field rations, high-altitude nutrition

WEAPONS
  * Rifles -- INSAS, AK-203, Dragunov SVD
  * Crew-Served -- NEGEV LMG, Carl Gustaf, 81mm Mortar

FIELD CRAFT
  * Navigation -- Map reading, compass, GPS/NAVIC
  * Camouflage -- Personal concealment techniques
  * Patrolling -- SMEAC orders, contact drills
  * Survival -- Water, food, shelter

TACTICAL
  * Counter-Insurgency -- Search, cordon ops, IED, Kargil lessons
  * NBC/CBRN -- Chemical, biological, nuclear, radiation
  * Mountain Warfare -- Climbing, avalanche awareness
  * Urban Warfare -- Room clearing, FIBUA

TECHNICAL
  * Communications -- Radio procedure, OPSEC, encryption
  * Vehicles -- BMP-2 Sarath, T-90 Bhishma, maintenance
  * Equipment -- Night vision devices
  * Signals -- Field radio types

ADMINISTRATIVE
  * Army Act, promotions, welfare schemes, ROE, casualty procedures

GENERAL KNOWLEDGE
  * Battle honours, rank structure, battalion organization, borders
"""
    print(help_text)


def format_sources(sources: list) -> str:
    """Format source references for display."""
    if not sources:
        return ""

    lines = ["\n  [Sources]"]
    for i, src in enumerate(sources, 1):
        lines.append(
            f"   {i}. [{src['category']}] {src['subcategory']} > {src['topic']}"
        )
    return "\n".join(lines)


def main():
    """Main chatbot loop."""
    print_banner()

    # Initialize the chatbot
    bot = MilitaryChatbot()

    try:
        bot.initialize()
    except RuntimeError as e:
        print(f"[ERROR] {e}")
        print("[TIP] Make sure Ollama is running and you've run 'python ingest.py' first.")
        sys.exit(1)
    except Exception as e:
        print(f"[ERROR] Failed to initialize: {e}")
        print("[TIP] Check that Ollama is running with Llama 3.2:")
        print("   ollama run llama3.2")
        sys.exit(1)

    # Main interaction loop
    while True:
        try:
            print(">> You: ", end="")
            user_input = input().strip()

            if not user_input:
                continue

            # Handle commands
            if user_input.lower() in ("/quit", "/exit", "quit", "exit"):
                print("\nJai Hind! Stay safe, soldier.\n")
                break

            if user_input.lower() in ("/clear", "/reset"):
                bot.reset_memory()
                continue

            if user_input.lower() in ("/help", "/topics"):
                print_help()
                continue

            # Get response from the chatbot
            print("\n[Analyzing...]\n")

            response = bot.ask(user_input)

            # Display the answer
            print(f"<< Sainik Sahayak:\n")
            print(f"   {response['answer']}\n")

            # Display sources
            source_text = format_sources(response["sources"])
            if source_text:
                print(source_text)

            print(f"\n{'=' * 60}\n")

        except KeyboardInterrupt:
            print("\n\nJai Hind! Stay safe, soldier.\n")
            break


if __name__ == "__main__":
    main()
