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


    function getBotReply(text) {
        var msg = text.toLowerCase()

        if (msg.includes('refund')) {
            return 'For refund requests please use the Refunds section on this page or email us at glideairways.support@gmail.com. Refunds are processed within 3-5 business days.'
        }
        if (msg.includes('book') || msg.includes('booking')) {
            return 'To manage your booking go to the Manage Booking page. You can change or cancel flights there as long as they have not departed yet.'
        }
        if (msg.includes('check in') || msg.includes('checkin') || msg.includes('check-in')) {
            return 'Online check-in opens 24 hours before your flight. Go to the Check-in page and have your booking reference and passport ready.'
        }
        if (msg.includes('baggage') || msg.includes('luggage') || msg.includes('bag')) {
            return 'Standard economy includes 1 carry-on bag up to 10kg. Extra baggage can be added through Manage Booking before your flight departs.'
        }
        if (msg.includes('delay') || msg.includes('delayed') || msg.includes('cancel') || msg.includes('cancelled')) {
            return 'If your flight is delayed or cancelled you will be notified by email. You may be entitled to compensation - raise a refund request below.'
        }
        if (msg.includes('membership') || msg.includes('points') || msg.includes('loyalty')) {
            return 'We offer Silver, Gold and Platinum membership tiers. Visit the Membership page to sign up for free and start earning points from your first booking.'
        }
        if (msg.includes('passport') || msg.includes('visa') || msg.includes('document')) {
            return 'You will need a valid passport or government ID and your booking reference to check in. For visa requirements check your destination country official embassy website.'
        }
        if (msg.includes('seat') || msg.includes('seats')) {
            return 'Seat selection is available during booking. You can also update your seat through Manage Booking before your flight departs.'
        }
        if (msg.includes('payment') || msg.includes('pay') || msg.includes('price') || msg.includes('cost')) {
            return 'We accept all major credit and debit cards. If you have a payment issue please contact us at glideairways.support@gmail.com.'
        }
        if (msg.includes('hello') || msg.includes('hi') || msg.includes('hey')) {
            return 'Hi there! How can we help you today? You can ask about bookings, check-in, baggage, refunds, or anything else.'
        }
        if (msg.includes('thank') || msg.includes('thanks')) {
            return 'You are welcome! Is there anything else we can help you with?'
        }
        return 'Thanks for your message. For urgent queries please email us at glideairways.support@gmail.com or call +44 000 000 0000 Monday to Friday 8am-8pm.'
    }

    function sendChat() {
        const input = byId('chatInput');
        if (!input) return;

        const text = input.value.trim();
        if (text === '') return;
        input.value = '';

        var typing = document.createElement('div');
        typing.classList.add('chat-msg', 'bot', 'typing-indicator');
        typing.textContent = '...';
        var messages = byId('chatMessages');
        if (messages) {
            messages.appendChild(typing);
            messages.scrollTop = messages.scrollHeight;
        }

        setTimeout(function() {
            if (messages && typing.parentNode) messages.removeChild(typing);
            var botMsg = document.createElement('div');
            botMsg.classList.add('chat-msg', 'bot');
            botMsg.textContent = getBotReply(text);
            if (messages) {
                messages.appendChild(botMsg);
                messages.scrollTop = messages.scrollHeight;
            }
        }, 1000);

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
                trackResult.innerHTML =
                    '<p class="form-success">Your refund is being processed and should arrive within 3-5 business days.</p>';
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
