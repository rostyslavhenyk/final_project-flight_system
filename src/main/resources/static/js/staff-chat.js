(function () {
    'use strict';

    const summaryPollMs = 8000;
    const openConversationPollMs = 1500;
    let openUserId = null;
    let openConversationPollId = null;

    function createMessage(message) {
        const row = document.createElement('div');
        row.classList.add('chat-convo-msg');
        row.classList.add(message.isStaff ? 'staff' : 'customer');

        const name = document.createElement('span');
        name.classList.add('chat-convo-name');
        name.textContent = message.senderName;

        const text = document.createElement('p');
        text.textContent = message.message;

        row.appendChild(name);
        row.appendChild(text);
        return row;
    }

    function createActionButton(text, userId, isOpen) {
        const button = document.createElement('button');
        button.type = 'button';
        button.dataset.chatToggle = '';
        button.dataset.userId = String(userId);
        button.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        button.setAttribute('aria-controls', 'chat-convo-messages-' + userId + ' chat-reply-form-' + userId);
        button.textContent = text;
        return button;
    }

    function createCloseForm(userId) {
        const closeForm = document.createElement('form');
        closeForm.method = 'post';
        closeForm.action = '/staff/chat/close';

        const closeInput = document.createElement('input');
        closeInput.type = 'hidden';
        closeInput.name = 'userId';
        closeInput.value = String(userId);

        const closeButton = document.createElement('button');
        closeButton.type = 'submit';
        closeButton.textContent = 'Close';

        closeForm.appendChild(closeInput);
        closeForm.appendChild(closeButton);
        return closeForm;
    }

    function createReplyForm(conversation) {
        const replyForm = document.createElement('form');
        replyForm.method = 'post';
        replyForm.action = '/staff/chat/reply';
        replyForm.classList.add('chat-reply-form');
        replyForm.id = 'chat-reply-form-' + conversation.userId;
        replyForm.hidden = conversation.userId !== openUserId;

        const userIdInput = document.createElement('input');
        userIdInput.type = 'hidden';
        userIdInput.name = 'userId';
        userIdInput.value = String(conversation.userId);

        const row = document.createElement('div');
        row.classList.add('chat-reply-row');

        const label = document.createElement('label');
        label.classList.add('sr-only');
        label.setAttribute('for', 'reply-' + conversation.userId);
        label.textContent = 'Reply to ' + conversation.userName;

        const input = document.createElement('input');
        input.id = 'reply-' + conversation.userId;
        input.type = 'text';
        input.name = 'message';
        input.placeholder = 'Type a reply...';
        input.required = true;

        const button = document.createElement('button');
        button.type = 'submit';
        button.textContent = 'Reply';

        row.appendChild(label);
        row.appendChild(input);
        row.appendChild(button);
        replyForm.appendChild(userIdInput);
        replyForm.appendChild(row);
        return replyForm;
    }

    function createConversation(summary) {
        const card = document.createElement('article');
        card.classList.add('chat-convo-card');
        card.dataset.userId = String(summary.userId);

        const head = document.createElement('div');
        head.classList.add('chat-convo-head');

        const identity = document.createElement('div');
        const title = document.createElement('h2');
        title.textContent = summary.userName;
        const email = document.createElement('p');
        email.textContent = summary.userEmail;
        const preview = document.createElement('p');
        preview.classList.add('chat-convo-preview');
        preview.textContent = summary.lastMessage || 'No messages yet';
        identity.appendChild(title);
        identity.appendChild(email);
        identity.appendChild(preview);

        const actions = document.createElement('div');
        actions.classList.add('chat-convo-actions');

        const unread = document.createElement('span');
        unread.classList.add('chat-unread-pill');
        unread.setAttribute('aria-live', 'polite');
        unread.setAttribute('aria-atomic', 'true');
        unread.textContent = 'Unread';
        unread.hidden = !summary.unread;

        const count = document.createElement('span');
        count.textContent = summary.messageCount + ' messages';

        const isOpen = summary.userId === openUserId;
        actions.appendChild(unread);
        actions.appendChild(count);
        actions.appendChild(createActionButton(isOpen ? 'Close chat' : 'Open', summary.userId, isOpen));
        actions.appendChild(createCloseForm(summary.userId));

        head.appendChild(identity);
        head.appendChild(actions);

        const messages = document.createElement('div');
        messages.id = 'chat-convo-messages-' + summary.userId;
        messages.classList.add('chat-convo-messages');
        messages.setAttribute('aria-label', 'Conversation with ' + summary.userName);
        messages.hidden = !isOpen;

        card.appendChild(head);
        card.appendChild(messages);
        card.appendChild(createReplyForm(summary));
        return card;
    }

    function staffIsTyping() {
        const active = document.activeElement;
        return active && active.matches('.chat-reply-row input') && active.value.trim() !== '';
    }

    function renderSummaries(summaries) {
        if (staffIsTyping()) return;

        const list = document.querySelector('[data-staff-chat-list]');
        const empty = document.querySelector('[data-staff-chat-empty]');
        if (!list) return;

        list.innerHTML = '';
        summaries.forEach(function (summary) {
            list.appendChild(createConversation(summary));
        });

        list.hidden = summaries.length === 0;
        if (empty) empty.hidden = summaries.length > 0;

        if (openUserId !== null) {
            loadOpenConversation();
        }
    }

    function renderOpenConversation(conversation) {
        if (staffIsTyping()) return;

        const card = document.querySelector('[data-user-id="' + conversation.userId + '"]');
        if (!card) return;

        const messages = card.querySelector('.chat-convo-messages');
        const replyForm = card.querySelector('.chat-reply-form');
        const toggle = card.querySelector('[data-chat-toggle]');
        if (!messages || !replyForm || !toggle) return;

        messages.innerHTML = '';
        conversation.messages.forEach(function (message) {
            messages.appendChild(createMessage(message));
        });
        messages.hidden = false;
        replyForm.hidden = false;
        toggle.textContent = 'Close chat';
        toggle.setAttribute('aria-expanded', 'true');
        messages.scrollTop = messages.scrollHeight;
    }

    function loadSummaries() {
        fetch('/staff/chat/conversations')
            .then(function (response) {
                if (!response.ok) return null;
                return response.json();
            })
            .then(function (summaries) {
                if (summaries) renderSummaries(summaries);
            })
            .catch(function () {});
    }

    function loadOpenConversation() {
        if (openUserId === null) return;

        fetch('/staff/chat/messages?userId=' + encodeURIComponent(openUserId))
            .then(function (response) {
                if (!response.ok) return null;
                return response.json();
            })
            .then(function (conversation) {
                if (conversation) renderOpenConversation(conversation);
            })
            .catch(function () {});
    }

    function stopOpenConversationPolling() {
        if (openConversationPollId !== null) {
            window.clearInterval(openConversationPollId);
            openConversationPollId = null;
        }
    }

    function startOpenConversationPolling() {
        stopOpenConversationPolling();
        loadOpenConversation();
        openConversationPollId = window.setInterval(loadOpenConversation, openConversationPollMs);
    }

    function closeOpenConversation() {
        const current = openUserId;
        openUserId = null;
        stopOpenConversationPolling();

        if (current !== null) {
            const card = document.querySelector('[data-user-id="' + current + '"]');
            if (!card) return;
            const messages = card.querySelector('.chat-convo-messages');
            const replyForm = card.querySelector('.chat-reply-form');
            const toggle = card.querySelector('[data-chat-toggle]');
            if (messages) messages.hidden = true;
            if (replyForm) replyForm.hidden = true;
            if (toggle) {
                toggle.textContent = 'Open';
                toggle.setAttribute('aria-expanded', 'false');
            }
        }
    }

    function toggleConversation(userId) {
        if (openUserId === userId) {
            closeOpenConversation();
            return;
        }

        closeOpenConversation();
        openUserId = userId;
        startOpenConversationPolling();
    }

    function wireToggleClicks() {
        document.addEventListener('click', function (event) {
            if (!event.target || !event.target.closest) return;
            const button = event.target.closest('[data-chat-toggle]');
            if (!button) return;
            toggleConversation(Number(button.dataset.userId));
        });
    }

    function init() {
        const list = document.querySelector('[data-staff-chat-list]');
        if (!list) return;
        const initialOpenUserId = Number(list.dataset.openUserId);
        if (initialOpenUserId > 0) {
            openUserId = initialOpenUserId;
            startOpenConversationPolling();
        }
        wireToggleClicks();
        loadSummaries();
        window.setInterval(loadSummaries, summaryPollMs);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
