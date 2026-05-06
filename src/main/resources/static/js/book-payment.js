/**
 * /book/payment — if the URL lacks paxSel (e.g. refresh), refill visible names from the same sessionStorage key as passenger + seat steps.
 */
(function () {
  'use strict';

  var STORAGE_PAX = 'glideBookingPaxNames';

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

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', hydratePaxNames);
  } else {
    hydratePaxNames();
  }
})();
