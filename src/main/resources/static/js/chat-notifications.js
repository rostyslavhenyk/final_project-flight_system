(function () {
    'use strict';

    const pollMs = 10000;

    function updateBadge(count) {
        const badge = document.querySelector('[data-user-chat-unread]');
        if (!badge) return;

        badge.textContent = String(count);
        badge.hidden = count === 0;
    }

    function loadSummary() {
        fetch('/chat/summary')
            .then(function (response) {
                if (!response.ok) return null;
                return response.json();
            })
            .then(function (summary) {
                if (summary) updateBadge(summary.unreadMessages);
            })
            .catch(function () {});
    }

    function init() {
        loadSummary();
        window.setInterval(loadSummary, pollMs);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
