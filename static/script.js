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

        chatMessages.appendChild(msgDiv);

        if (role === 'user') {
            msgDiv.innerHTML = `
                ${avatarHtml}
                <div class="msg-content">
                    ${typeof marked !== 'undefined' ? marked.parse(content) : content}
                </div>
            `;
            
            // Scroll to bottom
            window.scrollTo({
                top: document.body.scrollHeight,
                behavior: 'smooth'
            });
        } else {
            // Bot typewriter effect
            msgDiv.innerHTML = `
                ${avatarHtml}
                <div class="msg-content">
                    <span class="typing-cursor"></span>
                </div>
            `;

            const contentDiv = msgDiv.querySelector('.msg-content');
            let i = 0;
            const speed = 10; // Extremely fast, snappy updates (in milliseconds)
            
            const typeWriter = () => {
                if (i < content.length) {
                    // Adjust dynamic chunk size depending on text length to prevent long answers from lagging
                    let charsToAdd = 1;
                    if (content.length > 600) {
                        charsToAdd = 2;
                    }
                    if (content.length > 1200) {
                        charsToAdd = 3;
                    }

                    const partial = content.substring(0, i + charsToAdd);
                    i += charsToAdd;

                    let rendered = typeof marked !== 'undefined' ? marked.parse(partial) : partial;
                    contentDiv.innerHTML = rendered + '<span class="typing-cursor"></span>';

                    window.scrollTo({
                        top: document.body.scrollHeight,
                        behavior: 'smooth'
                    });

                    setTimeout(typeWriter, speed);
                } else {
                    // Final rendering complete
                    let finalRendered = typeof marked !== 'undefined' ? marked.parse(content) : content;
                    contentDiv.innerHTML = finalRendered;

                    // Add sources dynamically at the end
                    if (sources && sources.length > 0) {
                        const sourcesHtml = `
                            <div class="sources-box">
                                <h5><i class="fa-solid fa-book-bookmark"></i> KNOWLEDGE SOURCES</h5>
                                <ul>
                                    ${sources.map((s, i) => `<li>${i+1}. [${s.category}] ${s.subcategory} &rarr; ${s.topic}</li>`).join('')}
                                </ul>
                            </div>
                        `;
                        contentDiv.innerHTML += sourcesHtml;
                    }

                    window.scrollTo({
                        top: document.body.scrollHeight,
                        behavior: 'smooth'
                    });
                }
            };

            typeWriter();
        }
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
