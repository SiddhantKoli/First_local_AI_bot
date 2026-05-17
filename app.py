import streamlit as st
from chatbot import MilitaryChatbot

# Page configuration
st.set_page_config(
    page_title="Sainik Sahayak - Indian Military AI",
    page_icon="🇮🇳",
    layout="centered",
    initial_sidebar_state="expanded"
)

# Custom CSS for styling
st.markdown("""
<style>
    .main-title {
        color: #2e7d32;
        font-family: 'Arial Black', sans-serif;
        text-align: center;
        margin-bottom: 0px;
    }
    .sub-title {
        color: #555;
        font-style: italic;
        text-align: center;
        margin-bottom: 30px;
    }
    .source-box {
        background-color: #f1f8e9;
        border-left: 4px solid #7cb342;
        padding: 10px;
        margin-top: 10px;
        font-size: 0.9em;
        border-radius: 4px;
    }
    .stChatMessage {
        border-radius: 10px;
    }
</style>
""", unsafe_allow_html=True)

# Sidebar with information
with st.sidebar:
    st.image("https://upload.wikimedia.org/wikipedia/commons/e/e4/Indian_Army_Logo.svg", width=150)
    st.markdown("### 🇮🇳 Sainik Sahayak")
    st.markdown("AI Assistant for Indian Army Personnel.")
    st.markdown("---")
    
    st.markdown("### 📚 Topics Covered:")
    st.markdown("- **Medical & First Aid**")
    st.markdown("- **Weapons & Equipment**")
    st.markdown("- **Field Craft & Survival**")
    st.markdown("- **Tactical & NBC**")
    st.markdown("- **Technical & Comms**")
    st.markdown("- **Administrative & Welfare**")
    
    st.markdown("---")
    if st.button("🗑️ Clear Chat History"):
        if "bot" in st.session_state:
            st.session_state.bot.reset_memory()
        st.session_state.messages = []
        st.rerun()

# Title
st.markdown("<h1 class='main-title'>Sainik Sahayak (सैनिक सहायक)</h1>", unsafe_allow_html=True)
st.markdown("<p class='sub-title'>Local AI Assistant Powered by Llama 3.2</p>", unsafe_allow_html=True)

# Initialize chatbot and session state
if "bot" not in st.session_state:
    with st.spinner("Initializing AI Engine and connecting to Knowledge Base..."):
        try:
            bot = MilitaryChatbot()
            bot.initialize()
            st.session_state.bot = bot
        except Exception as e:
            st.error(f"Failed to initialize chatbot: {str(e)}")
            st.stop()

if "messages" not in st.session_state:
    st.session_state.messages = [
        {"role": "assistant", "content": "Jai Hind! 🇮🇳 I am Sainik Sahayak. How can I assist you today, soldier?", "sources": []}
    ]

# Display chat messages from history
for message in st.session_state.messages:
    with st.chat_message(message["role"]):
        st.markdown(message["content"])
        
        # Display sources if available
        if message.get("sources"):
            with st.expander("📄 View Sources"):
                for idx, src in enumerate(message["sources"], 1):
                    st.markdown(f"**{idx}.** [{src['category']}] {src['subcategory']} → {src['topic']}")

# Chat input mechanism
if prompt := st.chat_input("Ask a question about military procedures, weapons, etc..."):
    # Add user message to chat history
    st.session_state.messages.append({"role": "user", "content": prompt, "sources": []})
    
    # Display user message in chat message container
    with st.chat_message("user"):
        st.markdown(prompt)

    # Display assistant response
    with st.chat_message("assistant"):
        with st.spinner("Analyzing knowledge base..."):
            try:
                # Get response from backend
                response = st.session_state.bot.ask(prompt)
                
                answer = response["answer"]
                sources = response.get("sources", [])
                
                st.markdown(answer)
                
                if sources:
                    with st.expander("📄 View Sources"):
                        for idx, src in enumerate(sources, 1):
                            st.markdown(f"**{idx}.** [{src['category']}] {src['subcategory']} → {src['topic']}")
                
                # Add assistant response to chat history
                st.session_state.messages.append(
                    {"role": "assistant", "content": answer, "sources": sources}
                )
            except Exception as e:
                st.error(f"Error generating response: {str(e)}")
