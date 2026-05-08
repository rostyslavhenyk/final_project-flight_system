(function () {
    'use strict';

    let chatPollId = null;
    const chatPollMs = 8000;

    function byId(id) {
        return document.getElementById(id);
    }

    function switchRefundTab(tabName) {
        const trackPanel = byId('track');
        const requestPanel = byId('request');
        const trackTab = byId('tab-track');
        const requestTab = byId('tab-request');
        const activePanel = byId(tabName);
        const activeTab = byId('tab-' + tabName);

        if (!trackPanel || !requestPanel || !trackTab || !requestTab || !activePanel || !activeTab) return;

        trackPanel.hidden = true;
        requestPanel.hidden = true;
        trackTab.classList.remove('active');
        requestTab.classList.remove('active');
        trackTab.setAttribute('aria-selected', 'false');
        requestTab.setAttribute('aria-selected', 'false');

        activePanel.hidden = false;
        activeTab.classList.add('active');
        activeTab.setAttribute('aria-selected', 'true');
    }

    function renderChatStatus(text) {
        const messages = byId('chatMessages');
        if (!messages) return;

        messages.innerHTML = '';
        const status = document.createElement('div');
        status.classList.add('chat-msg', 'system');
        status.textContent = text;
        messages.appendChild(status);
    }

    function renderChatMessages(data) {
        const messages = byId('chatMessages');
        if (!messages) return;

        messages.innerHTML = '';
        if (data.length === 0) {
            renderChatStatus('Send a message and staff will reply here.');
            return;
        }

        data.forEach(function (item) {
            const message = document.createElement('div');
            message.classList.add('chat-msg');
            message.classList.add(item.isStaff ? 'bot' : 'user');
            message.textContent = item.senderName + ': ' + item.message;
            messages.appendChild(message);
        });

        messages.scrollTop = messages.scrollHeight;
    }

    function loadReplies() {
        fetch('/chat/messages')
            .then(function (response) {
                if (response.status === 401) {
                    renderChatStatus('Log in to start a live chat with staff.');
                    return null;
                }

                if (!response.ok) return null;
                return response.json();
            })
            .then(function (data) {
                if (data) renderChatMessages(data);
            })
            .catch(function () {
                renderChatStatus('Live chat is unavailable right now.');
            });
    }

    function startChatPolling() {
        if (chatPollId !== null) return;
        chatPollId = window.setInterval(loadReplies, chatPollMs);
    }

    function stopChatPolling() {
        if (chatPollId === null) return;
        window.clearInterval(chatPollId);
        chatPollId = null;
    }

    function toggleChat() {
        const chatBody = byId('chatBody');
        const chatHeader = document.querySelector('.chat-header');
        const chevron = byId('chatChevron');
        const input = byId('chatInput');
        if (!chatBody || !chatHeader || !chevron || !input) return;

        if (chatBody.hidden) {
            chatBody.hidden = false;
            chatHeader.setAttribute('aria-expanded', 'true');
            chevron.textContent = 'v';
            input.focus();
            loadReplies();
            startChatPolling();
        } else {
            chatBody.hidden = true;
            chatHeader.setAttribute('aria-expanded', 'false');
            chevron.textContent = '^';
            stopChatPolling();
        }
    }

    function openChat() {
        const chatBody = byId('chatBody');
        if (!chatBody) return;

        if (chatBody.hidden) {
            toggleChat();
        } else {
            loadReplies();
        }
    }

    function sendChat() {
        const input = byId('chatInput');
        if (!input) return;

        const text = input.value.trim();
        if (text === '') return;
        input.value = '';

        fetch('/chat/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'message=' + encodeURIComponent(text),
        })
            .then(function (response) {
                if (response.status === 401) {
                    renderChatStatus('Log in to send a live chat message to staff.');
                    return;
                }

                if (!response.ok) {
                    renderChatStatus('Your message could not be sent. Please try again.');
                    return;
                }

                loadReplies();
            })
            .catch(function () {
                renderChatStatus('Your message could not be sent. Please try again.');
            });
    }

    function wireFaqAccordion() {
        document.querySelectorAll('.faq-question').forEach(function (button) {
            button.addEventListener('click', function () {
                const answer = byId(button.getAttribute('aria-controls'));
                if (!answer) return;

                const expanded = answer.hidden;
                answer.hidden = !expanded;
                button.setAttribute('aria-expanded', expanded ? 'true' : 'false');
            });
        });
    }

    function wireFaqSearch() {
        const search = byId('faqSearch');
        if (!search) return;

        search.addEventListener('input', function () {
            const typed = search.value.toLowerCase();
            document.querySelectorAll('.faq-item').forEach(function (item) {
                const question = item.querySelector('.faq-question');
                if (!question) return;
                item.style.display = question.textContent.toLowerCase().includes(typed) ? 'block' : 'none';
            });
        });
    }

    function wireRefundTabs() {
        document.querySelectorAll('[data-refund-tab]').forEach(function (button) {
            button.addEventListener('click', function () {
                switchRefundTab(button.getAttribute('data-refund-tab'));
            });
        });
    }

    function wireRefundPlaceholders() {
        const trackForm = byId('track-refund-form');
        const requestForm = byId('request-refund-form');
        const trackResult = byId('trackResult');
        const requestResult = byId('requestResult');

        if (trackForm && trackResult) {
            trackForm.addEventListener('submit', function (event) {
                event.preventDefault();

                var ref = document.getElementById('track-ref').value;
                var email = document.getElementById('track-email').value;
                var url = '/help/refund/status?ref=' + encodeURIComponent(ref) +
                    '&email=' + encodeURIComponent(email);

                fetch(url)
                    .then(function (response) {
                        return response.text().then(function (text) {
                            return { ok: response.ok, text: text };
                        });
                    })
                    .then(function (result) {
                        var className = result.ok ? 'form-success' : 'form-error';
                        trackResult.innerHTML = '<p class="' + className + '">' + result.text + '</p>';
                    })
                    .catch(function () {
                        trackResult.innerHTML = '<p class="form-error">Refund tracking is unavailable right now.</p>';
                    });
            });
        }

        if (requestForm && requestResult) {
            requestForm.addEventListener('submit', function (event) {
                event.preventDefault();

                var firstname = document.getElementById('refund-firstname').value;
                var lastname = document.getElementById('refund-lastname').value;
                var email = document.getElementById('refund-email').value;
                var ref = document.getElementById('refund-ref').value;
                var reason = document.getElementById('refund-reason').value;
                var details = document.getElementById('refund-details').value;

                var body = 'firstname=' + encodeURIComponent(firstname) +
                    '&lastname=' + encodeURIComponent(lastname) +
                    '&email=' + encodeURIComponent(email) +
                    '&ref=' + encodeURIComponent(ref) +
                    '&reason=' + encodeURIComponent(reason) +
                    '&details=' + encodeURIComponent(details);

                fetch('/help/refund', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: body
                })
                .then(function (response) {
                    if (response.ok) {
                        requestResult.innerHTML = '<p class="form-success">Your request has been submitted. You will receive a confirmation email shortly.</p>';
                    } else {
                        requestResult.innerHTML = '<p style="color:#d32f2f;">Something went wrong. Please try again.</p>';
                    }
                });
            });
        }
    }

    function wireChat() {
        const openButton = byId('open-chat-button');
        const toggleButton = byId('chat-toggle-button');
        const sendButton = byId('chat-send-button');
        const input = byId('chatInput');

        if (openButton) openButton.addEventListener('click', openChat);
        if (toggleButton) toggleButton.addEventListener('click', toggleChat);
        if (sendButton) sendButton.addEventListener('click', sendChat);
        if (input) {
            input.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') sendChat();
            });
        }
    }

    function wireContactForm() {
        var form = document.getElementById("contact-form");
        var result = document.getElementById("contactResult");
        if (!form || !result) return;
        form.addEventListener("submit", function(event) {
            event.preventDefault();
            var data = new FormData(form);
            fetch("/help/tickets", {
                method: "POST",
                body: data
            }).then(function(response) {
                if (response.ok || response.redirected) {
                    result.innerHTML = '<p class="form-success">Message sent. Your support ticket has been created.</p>';
                    form.reset();
                } else {
                    result.innerHTML = '<p style="color:#d32f2f;">Something went wrong. Please try again.</p>';
                }
            }).catch(function() {
                result.innerHTML = '<p style="color:#d32f2f;">Something went wrong. Please try again.</p>';
            });
        });
    }

    function init() {
        wireFaqAccordion();
        wireFaqSearch();
        wireRefundTabs();
        wireRefundPlaceholders();
        wireChat();
        wireContactForm();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
