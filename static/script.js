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

    // Pool of dynamic prompts for Quick Access categories
    const promptPools = {
        procedures: [
            "What are the standard operating procedures for patrols?",
            "Explain the duties and routine of an Observation Post.",
            "What is the structure and protocol of a patrol order (SMEAC)?",
            "How do we execute a peel drill to break contact under fire?"
        ],
        weapons: [
            "Explain the specifications and usage of the AK-203 rifle.",
            "What is the caliber, effective range, and magazine capacity of the INSAS rifle?",
            "What are the safety regulations and misfire drills for the 81mm Mortar?",
            "Explain the specifications and backblast danger area of the Carl Gustaf recoilless rifle."
        ],
        tactics: [
            "What are the standard room clearing tactics in urban warfare?",
            "Explain the key tactical principles of Jungle Warfare.",
            "What are the main stages of a Cordon and Search Operation (CASO)?",
            "How does a section execute immediate action drills upon enemy contact?"
        ],
        equipment: [
            "What are the capabilities of the BMP-2 Sarath?",
            "What is the armament and protective armor of the T-90 Bhishma tank?",
            "How do we conduct daily preventive maintenance (PMCS) for military vehicles?",
            "What are the emergency evacuation procedures for a BMP-2 crew?"
        ],
        medical: [
            "How do you apply a combat tourniquet?",
            "What is the immediate treatment for a tension pneumothorax in the field?",
            "How do we prevent and treat hypothermia in high-altitude postings like Siachen?",
            "What are the symptoms and immediate field treatments for High Altitude Pulmonary Edema (HAPE)?"
        ],
        intel: [
            "What are the key lessons from the Kargil War?",
            "How do we perform resection to find our position using a map and compass?",
            "What is the format and criteria for reporting enemy activity (SALUTE format)?",
            "What are the key indicators for recognizing an Improvised Explosive Device (IED)?"
        ]
    };

    // Tracking indices for each category rotation
    const promptIndices = {
        procedures: 0,
        weapons: 0,
        tactics: 0,
        equipment: 0,
        medical: 0,
        intel: 0
    };

    // Rotate and insert the next question for a given category, clearing the previous value first
    window.rotateCategoryPrompt = (category) => {
        if (!promptPools[category]) return;
        
        const pool = promptPools[category];
        const currentIndex = promptIndices[category];
        
        // Clear previous input completely
        promptInput.value = '';
        
        // Insert new dynamic prompt
        promptInput.value = pool[currentIndex];
        promptInput.focus();
        
        // Rotate index sequentially for next click
        promptIndices[category] = (currentIndex + 1) % pool.length;
    };

    const appendMessage = (role, content) => {
        const msgDiv = document.createElement('div');
        msgDiv.className = `message ${role}`;

        let avatarHtml = `<div class="avatar"><i class="fa-solid fa-user"></i></div>`;

        msgDiv.innerHTML = `
            ${avatarHtml}
            <div class="msg-content">
                ${typeof marked !== 'undefined' ? marked.parse(content) : content}
            </div>
        `;

        chatMessages.appendChild(msgDiv);

        // Scroll to bottom
        window.scrollTo({
            top: document.body.scrollHeight,
            behavior: 'smooth'
        });
    };

    const runTypewriter = (contentDiv, content, sources = []) => {
        let i = 0;
        const speed = 10; // Snappy update interval

        const typeWriter = () => {
            if (i < content.length) {
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
                // Done typing, set final parsed markdown
                let finalRendered = typeof marked !== 'undefined' ? marked.parse(content) : content;
                contentDiv.innerHTML = finalRendered;

                // Add sources if present
                if (sources && sources.length > 0) {
                    const sourcesHtml = `
                        <div class="sources-box">
                            <h5><i class="fa-solid fa-book-bookmark"></i> KNOWLEDGE SOURCES</h5>
                            <ul>
                                ${sources.map((s, idx) => `<li>${idx+1}. [${s.category}] ${s.subcategory} &rarr; ${s.topic}</li>`).join('')}
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

        // Add thinking bot message bubble immediately
        const botMsgDiv = document.createElement('div');
        botMsgDiv.className = 'message bot';
        botMsgDiv.innerHTML = `
            <div class="avatar"><img src="bot_icon.png" alt="AI"></div>
            <div class="msg-content thinking">
                <div class="typing-indicator">
                    <span></span>
                    <span></span>
                    <span></span>
                </div>
            </div>
        `;
        chatMessages.appendChild(botMsgDiv);

        window.scrollTo({
            top: document.body.scrollHeight,
            behavior: 'smooth'
        });

        try {
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ prompt: prompt })
            });

            const data = await response.json();
            
            const contentDiv = botMsgDiv.querySelector('.msg-content');
            contentDiv.classList.remove('thinking');

            if (data.success) {
                runTypewriter(contentDiv, data.data.answer, data.data.sources);
            } else {
                contentDiv.innerHTML = `**Error:** ${data.error}`;
            }
        } catch (error) {
            const contentDiv = botMsgDiv.querySelector('.msg-content');
            if (contentDiv) {
                contentDiv.classList.remove('thinking');
                contentDiv.innerHTML = `**Network Error:** Could not connect to the server.`;
            }
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
