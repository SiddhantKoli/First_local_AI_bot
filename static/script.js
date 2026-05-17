document.addEventListener('DOMContentLoaded', () => {
    const promptInput = document.getElementById('prompt-input');
    const sendBtn = document.getElementById('send-btn');
    const heroSection = document.getElementById('hero-section');
    const chatSection = document.getElementById('chat-section');
    const chatMessages = document.getElementById('chat-messages');
    const loadingOverlay = document.getElementById('loading-overlay');

    // Make insertPrompt available globally for onclick events in HTML
    window.insertPrompt = (text) => {
        promptInput.value = text;
        promptInput.focus();
    };

    const appendMessage = (role, content, sources = []) => {
        const msgDiv = document.createElement('div');
        msgDiv.className = `message ${role}`;

        let avatarHtml = '';
        if (role === 'user') {
            avatarHtml = `<div class="avatar"><i class="fa-solid fa-user"></i></div>`;
        } else {
            avatarHtml = `<div class="avatar"><img src="bot_icon.png" alt="AI"></div>`;
        }

        // Use marked.js if available, otherwise just use text
        let formattedContent = content;
        if (typeof marked !== 'undefined') {
            formattedContent = marked.parse(content);
        }

        let sourcesHtml = '';
        if (sources && sources.length > 0) {
            sourcesHtml = `
                <div class="sources-box">
                    <h5><i class="fa-solid fa-book-bookmark"></i> KNOWLEDGE SOURCES</h5>
                    <ul>
                        ${sources.map((s, i) => `<li>${i+1}. [${s.category}] ${s.subcategory} &rarr; ${s.topic}</li>`).join('')}
                    </ul>
                </div>
            `;
        }

        msgDiv.innerHTML = `
            ${avatarHtml}
            <div class="msg-content">
                ${formattedContent}
                ${sourcesHtml}
            </div>
        `;

        chatMessages.appendChild(msgDiv);
        
        // Scroll to bottom
        window.scrollTo({
            top: document.body.scrollHeight,
            behavior: 'smooth'
        });
    };

    const sendMessage = async () => {
        const prompt = promptInput.value.trim();
        if (!prompt) return;

        // Hide hero, show chat on first message
        if (heroSection.style.display !== 'none') {
            heroSection.style.display = 'none';
            chatSection.style.display = 'flex';
        }

        // Add user message
        appendMessage('user', prompt);
        promptInput.value = '';
        loadingOverlay.style.display = 'flex';

        try {
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ prompt: prompt })
            });

            const data = await response.json();
            
            loadingOverlay.style.display = 'none';

            if (data.success) {
                appendMessage('bot', data.data.answer, data.data.sources);
            } else {
                appendMessage('bot', `**Error:** ${data.error}`);
            }
        } catch (error) {
            loadingOverlay.style.display = 'none';
            appendMessage('bot', `**Network Error:** Could not connect to the server.`);
            console.error('Error:', error);
        }
    };

    // Event listeners
    sendBtn.addEventListener('click', sendMessage);

    promptInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            sendMessage();
        }
    });
});
