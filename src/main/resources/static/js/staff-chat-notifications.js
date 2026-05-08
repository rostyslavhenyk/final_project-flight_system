(function () {
    'use strict';

    const pollMs = 10000;

    function updateBadge(count) {
        const badge = document.querySelector('[data-staff-chat-unread]');
        if (!badge) return;

        badge.textContent = String(count);
        badge.hidden = count === 0;
    }

    function updateDashboardCount(count) {
        const counter = document.querySelector('[data-staff-chat-dashboard-count]');
        if (counter) counter.textContent = String(count);
    }

    function loadSummary() {
        fetch('/staff/chat/summary')
            .then(function (response) {
                if (!response.ok) return null;
                return response.json();
            })
            .then(function (summary) {
                if (!summary) return;
                updateBadge(summary.unreadConversations);
                updateDashboardCount(summary.unreadConversations);
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
