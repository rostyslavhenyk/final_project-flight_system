/**
 * My account - Manage booking tab.
 * Paid bookings are rendered by the server from the database for the logged-in account.
 */
(function () {
  'use strict';

  const STORAGE_PAX = 'glideBookingPaxNames';

  function hydratePaxCells(root) {
    if (!root) return;
    try {
      const raw = sessionStorage.getItem(STORAGE_PAX);
      if (!raw) return;
      const list = JSON.parse(raw);
      if (!Array.isArray(list)) return;
      root.querySelectorAll('[data-pay-pax-slot]').forEach(function (cell) {
        const slotStr = cell.getAttribute('data-pay-pax-slot');
        if (!slotStr) return;
        const slot = parseInt(slotStr, 10);
        const el = cell.querySelector('[data-pay-pax-name-text]');
        if (!el) return;
        for (let i = 0; i < list.length; i++) {
          const entry = list[i];
          if (!entry || parseInt(entry.slot, 10) !== slot) continue;
          const name = String(entry.displayName || '')
            .replace(/\s+/g, ' ')
            .trim();
          if (name) el.textContent = name;
          break;
        }
      });
    } catch (e) {}
  }

  function wireTabs() {
    const detailTab = document.getElementById('tab-account-details');
    const manageTab = document.getElementById('tab-manage-booking');
    const detailPanel = document.getElementById('panel-account-details');
    const managePanel = document.getElementById('panel-manage-booking');
    if (!detailTab || !manageTab || !detailPanel || !managePanel) return;

    function activate(which) {
      const isManage = which === 'manage';
      detailTab.setAttribute('aria-selected', isManage ? 'false' : 'true');
      manageTab.setAttribute('aria-selected', isManage ? 'true' : 'false');
      detailPanel.toggleAttribute('hidden', !!isManage);
      managePanel.toggleAttribute('hidden', !isManage);
    }

    detailTab.addEventListener('click', function () {
      activate('details');
    });
    manageTab.addEventListener('click', function () {
      activate('manage');
    });

    activate(window.location.hash === '#manage-booking' ? 'manage' : 'details');
  }

  function wireNameEditToggle() {
    const btn = document.getElementById('account-name-edit-toggle');
    const form = document.getElementById('account-name-edit-form');
    if (!btn || !form) return;

    btn.addEventListener('click', function () {
      const open = !form.hidden;
      form.hidden = open;
      btn.setAttribute('aria-expanded', open ? 'false' : 'true');

      if (!open) {
        const first = form.querySelector('input[name="firstName"]');
        if (first) first.focus();
      }
    });
  }

  function wireManageCardsAndModal() {
    const emptyEl = document.getElementById('manage-booking-empty');
    const listEl = document.getElementById('manage-booking-list');
    const dialog = document.getElementById('fare-summary-dialog');
    const bodyEl = document.getElementById('fare-summary-dialog-body');
    const closeBtn = document.getElementById('fare-summary-close-btn');

    if (!listEl || !dialog || !bodyEl || !closeBtn) return;

    const currentCards = listEl.querySelectorAll('[data-fare-summary-href]');
    const allCards = document.querySelectorAll('[data-fare-summary-href]');
    if (!currentCards.length) {
      listEl.hidden = true;
      if (emptyEl) emptyEl.hidden = false;
    } else {
      if (emptyEl) emptyEl.hidden = true;
      listEl.hidden = false;
    }

    function closeDialog() {
      if (typeof dialog.close === 'function') {
        dialog.close();
      }
    }

    function openFareSummary(href) {
      bodyEl.innerHTML = '<p class="empty-state" role="status">Loading fare summary...</p>';
      if (typeof dialog.showModal === 'function') dialog.showModal();
      fetch(href, {
        credentials: 'same-origin',
        headers: {
          Accept: 'text/html'
        },
      })
        .then(function (r) {
          if (!r.ok) throw new Error('bad status');
          return r.text();
        })
        .then(function (html) {
          bodyEl.innerHTML = html;
          hydratePaxCells(bodyEl);
        })
        .catch(function () {
          bodyEl.innerHTML =
            '<p class="empty-state" role="alert">We could not load your fare summary. Try again shortly.</p>';
        });
    }

    allCards.forEach(function (card) {
      card.addEventListener('click', function () {
        const href = card.getAttribute('data-fare-summary-href');
        if (href) openFareSummary(href);
      });
    });

    closeBtn.addEventListener('click', closeDialog);
    dialog.addEventListener('click', function (e) {
      if (e.target === dialog && typeof dialog.close === 'function') {
        dialog.close();
      }
    });
    document.addEventListener('keydown', function (e) {
      if (e.key !== 'Escape') return;
      if (!dialog.open) return;
      closeDialog();
    });
  }

  function onReady() {
    wireTabs();
    wireNameEditToggle();
    wireManageCardsAndModal();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', onReady);
  } else {
    onReady();
  }
})();
