/**
 * My account — Manage booking tab: paid trips from localStorage (see book-payment.js), sorted by outbound
 * departure date, each card opens the fare summary dialog.
 */
(function () {
  'use strict';

  var STORAGE_QUERY_LEGACY = 'glideManagedBookingQuery';
  var STORAGE_BOOKINGS_LIST = 'glideManagedBookings';
  var STORAGE_PAX = 'glideBookingPaxNames';

  function loadManagedBookings() {
    try {
      var parsed = JSON.parse(localStorage.getItem(STORAGE_BOOKINGS_LIST) || 'null');
      if (Array.isArray(parsed) && parsed.length) {
        return parsed.filter(function (x) {
          return x && typeof x.qs === 'string' && x.qs.length > 0;
        });
      }
    } catch (e1) {}
    try {
      var leg = localStorage.getItem(STORAGE_QUERY_LEGACY);
      if (leg) return [{ qs: leg, at: 0, pax: null }];
    } catch (e2) {}
    return [];
  }

  /** Outbound departure for sort: URL `depart=YYYY-MM-DD` (soonest trips first). */
  function outboundDepartTime(entry) {
    try {
      var p = new URLSearchParams(entry.qs);
      var d = (p.get('depart') || '').trim();
      var t = d ? Date.parse(d) : NaN;
      if (!isNaN(t)) return t;
    } catch (e) {}
    return typeof entry.at === 'number' ? entry.at : 0;
  }

  function sortBookingsSoonestDepartFirst(entries) {
    return entries.slice().sort(function (a, b) {
      var ta = outboundDepartTime(a);
      var tb = outboundDepartTime(b);
      if (ta !== tb) return ta - tb;
      return (typeof a.at === 'number' ? a.at : 0) - (typeof b.at === 'number' ? b.at : 0);
    });
  }

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

  function cardLinesFromBookingQuery(qs) {
    var p = new URLSearchParams(qs);
    var fromRaw = (p.get('from') || '').trim();
    var toRaw = (p.get('to') || '').trim();
    var depart = (p.get('depart') || '').trim();
    var trip = (p.get('trip') || '').toLowerCase() === 'return' ? 'Return' : 'One way';
    var adults = parseInt(p.get('adults') || '1', 10) || 1;
    var ch = parseInt(p.get('children') || '0', 10) || 0;
    var route = '';
    if (fromRaw && toRaw) {
      route = fromRaw + ' → ' + toRaw;
    } else if (fromRaw || toRaw) {
      route = (fromRaw || toRaw).trim();
    } else {
      route = 'Your flight';
    }
    var metaBits = [];
    if (depart) metaBits.push(depart);
    metaBits.push(trip);
    var paxParts = [];
    paxParts.push(adults + (adults === 1 ? ' adult' : ' adults'));
    if (ch > 0) {
      paxParts.push(ch + (ch === 1 ? ' child' : ' children'));
    }
    metaBits.push(paxParts.join(', '));
    return { route: route, meta: metaBits.join(' · ') };
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

    if (window.location.hash === '#manage-booking') {
      activate('manage');
    } else {
      activate('details');
    }
  }

  function wireManageCardsAndModal() {
    var emptyEl = document.getElementById('manage-booking-empty');
    var listEl = document.getElementById('manage-booking-list');
    var dialog = document.getElementById('fare-summary-dialog');
    var bodyEl = document.getElementById('fare-summary-dialog-body');
    var closeBtn = document.getElementById('fare-summary-close-btn');

    if (!listEl || !dialog || !bodyEl || !closeBtn) return;

    var entries = sortBookingsSoonestDepartFirst(loadManagedBookings());

    if (!entries.length) {
      listEl.hidden = true;
      listEl.innerHTML = '';
      if (emptyEl) emptyEl.hidden = false;
      return;
    }

    if (emptyEl) emptyEl.hidden = true;
    listEl.hidden = false;
    listEl.innerHTML = '';

    function closeDialog() {
      if (typeof dialog.close === 'function') {
        dialog.close();
      }
    }

    function openFareSummary(qs, paxSnap) {
      bodyEl.innerHTML = '<p class="empty-state" role="status">Loading fare summary…</p>';
      if (typeof dialog.showModal === 'function') dialog.showModal();
      if (paxSnap) {
        try {
          sessionStorage.setItem(STORAGE_PAX, paxSnap);
        } catch (e) {}
      }
      fetch('/account/fare-summary?' + qs, {
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

    entries.forEach(function (entry, idx) {
      var qs = entry.qs.trim();
      if (!qs) return;
      var lines = cardLinesFromBookingQuery(qs);
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'manage-booking-card';
      btn.setAttribute('aria-label', 'Open fare summary for booking ' + (idx + 1));
      var eyebrow = document.createElement('p');
      eyebrow.className = 'manage-booking-card__eyebrow';
      eyebrow.textContent = entries.length > 1 ? 'Booking ' + (idx + 1) : 'Latest booking';
      var route = document.createElement('p');
      route.className = 'manage-booking-card__route';
      route.textContent = lines.route;
      var meta = document.createElement('p');
      meta.className = 'manage-booking-card__meta';
      meta.textContent = lines.meta;
      btn.appendChild(eyebrow);
      btn.appendChild(route);
      btn.appendChild(meta);
      var paxSnap = typeof entry.pax === 'string' ? entry.pax : null;
      btn.addEventListener('click', function () {
        openFareSummary(qs, paxSnap);
      });
      listEl.appendChild(btn);
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
    wireManageCardsAndModal();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', onReady);
  } else {
    onReady();
  }
})();
