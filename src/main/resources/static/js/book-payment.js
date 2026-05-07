/**
 * /book/payment — if the URL lacks paxSel (e.g. refresh), refill visible names from the same sessionStorage key as passenger + seat steps.
 * Pay now persists the booking query string for My Account → Manage booking, then redirects.
 */
(function () {
  'use strict';

  var STORAGE_PAX = 'glideBookingPaxNames';
  /** Legacy single-booking key; superseded by glideManagedBookings JSON array */
  var STORAGE_MANAGED_BOOKING_QUERY = 'glideManagedBookingQuery';
  var STORAGE_BOOKINGS_LIST = 'glideManagedBookings';

  function rememberBookingAndRedirect() {
    try {
      var qs = window.location.search;
      var raw = qs.length > 1 ? qs.slice(1) : '';
      if (!raw) {
        window.location.href = '/my-account#manage-booking';
        return;
      }
      var list = [];
      try {
        var parsed = JSON.parse(localStorage.getItem(STORAGE_BOOKINGS_LIST) || 'null');
        if (Array.isArray(parsed)) list = parsed;
      } catch (e2) {}
      if (list.length === 0) {
        try {
          var legacy = localStorage.getItem(STORAGE_MANAGED_BOOKING_QUERY);
          if (legacy)
            list = [{ qs: legacy, at: Date.now(), pax: sessionStorage.getItem(STORAGE_PAX) }];
        } catch (e3) {}
      }
      var paxSnap = null;
      try {
        paxSnap = sessionStorage.getItem(STORAGE_PAX);
      } catch (e4) {}
      list = list.filter(function (x) {
        return x && typeof x.qs === 'string' && x.qs !== raw;
      });
      list.push({ qs: raw, at: Date.now(), pax: paxSnap });
      localStorage.setItem(STORAGE_BOOKINGS_LIST, JSON.stringify(list));
      try {
        localStorage.removeItem(STORAGE_MANAGED_BOOKING_QUERY);
      } catch (e5) {}
    } catch (e) {}
    window.location.href = '/my-account#manage-booking';
  }

  function wirePayNow() {
    var btn = document.getElementById('pay-now-button');
    if (!btn) return;
    btn.addEventListener('click', rememberBookingAndRedirect);
  }

  function hydratePaxNames() {
    try {
      var raw = sessionStorage.getItem(STORAGE_PAX);
      if (!raw) return;
      var list = JSON.parse(raw);
      if (!Array.isArray(list)) return;
      document.querySelectorAll('[data-pay-pax-slot]').forEach(function (cell) {
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

  function init() {
    hydratePaxNames();
    wirePayNow();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
