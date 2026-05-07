/**
 * My account - Manage booking tab.
 * Paid bookings are rendered by the server from the database for the logged-in account.
 */
(function () {
  'use strict';

  var STORAGE_PAX = 'glideBookingPaxNames';

  function hydratePaxCells(root) {
    if (!root) return;
    try {
      var raw = sessionStorage.getItem(STORAGE_PAX);
      if (!raw) return;
      var list = JSON.parse(raw);
      if (!Array.isArray(list)) return;
      root.querySelectorAll('[data-pay-pax-slot]').forEach(function (cell) {
        var slotStr = cell.getAttribute('data-pay-pax-slot');
        if (!slotStr) return;
        var slot = parseInt(slotStr, 10);
        var el = cell.querySelector('[data-pay-pax-name-text]');
        if (!el) return;
        for (var i = 0; i < list.length; i++) {
          var entry = list[i];
          if (!entry || parseInt(entry.slot, 10) !== slot) continue;
          var nm = String(entry.displayName || '')
            .replace(/\s+/g, ' ')
            .trim();
          if (nm) el.textContent = nm;
          break;
        }
      });
    } catch (e) {}
  }

  function wireTabs() {
    var detailTab = document.getElementById('tab-account-details');
    var manageTab = document.getElementById('tab-manage-booking');
    var detailPanel = document.getElementById('panel-account-details');
    var managePanel = document.getElementById('panel-manage-booking');
    if (!detailTab || !manageTab || !detailPanel || !managePanel) return;

    function activate(which) {
      var isManage = which === 'manage';
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
    var btn = document.getElementById('account-name-edit-toggle');
    var form = document.getElementById('account-name-edit-form');
    if (!btn || !form) return;

    btn.addEventListener('click', function () {
      var open = !form.hidden;
      form.hidden = open;
      btn.setAttribute('aria-expanded', open ? 'false' : 'true');

      if (!open) {
        var first = form.querySelector('input[name="firstName"]');
        if (first) first.focus();
      }
    });
  }

  function wireManageCardsAndModal() {
    var emptyEl = document.getElementById('manage-booking-empty');
    var listEl = document.getElementById('manage-booking-list');
    var dialog = document.getElementById('fare-summary-dialog');
    var bodyEl = document.getElementById('fare-summary-dialog-body');
    var closeBtn = document.getElementById('fare-summary-close-btn');

    if (!listEl || !dialog || !bodyEl || !closeBtn) return;

    var currentCards = listEl.querySelectorAll('[data-fare-summary-href]');
    var allCards = document.querySelectorAll('[data-fare-summary-href]');
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
        var href = card.getAttribute('data-fare-summary-href');
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
