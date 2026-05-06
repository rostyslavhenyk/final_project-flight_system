/**
 * /book/seats — journey / leg switching, passenger selection, and seat assignment (sessionStorage).
 * Colours come from CSS variables in base.css (see seats.css).
 */
(function () {
  'use strict';

  var STORAGE_ASSIGN = 'glideSeatAssignmentsV1';
  var STORAGE_ASSIGN_FALLBACK = 'glideSeatAssignmentsV1Local';
  var STORAGE_PAX = 'glideBookingPaxNames';

  function decodeB64Utf8(b64) {
    var binary = atob(b64);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return new TextDecoder('utf-8').decode(bytes);
  }

  function loadSeatMap() {
    try {
      var raw = sessionStorage.getItem(STORAGE_ASSIGN) || localStorage.getItem(STORAGE_ASSIGN_FALLBACK);
      if (!raw) return {};
      var o = JSON.parse(raw);
      return typeof o === 'object' && o !== null ? o : {};
    } catch (e) {
      return {};
    }
  }

  function saveSeatMap(map) {
    try {
      var encoded = JSON.stringify(map);
      sessionStorage.setItem(STORAGE_ASSIGN, encoded);
      localStorage.setItem(STORAGE_ASSIGN_FALLBACK, encoded);
    } catch (e) {}
  }

  function toBase64UrlUtf8(text) {
    var encoded = btoa(unescape(encodeURIComponent(text)));
    return encoded.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  }

  function hashUnavailable(journeyKey, legIndex, seatId) {
    var s = journeyKey + '|' + legIndex + '|' + seatId;
    var h = 2166136261;
    for (var i = 0; i < s.length; i++) {
      h ^= s.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return Math.abs(h) % 10 === 0;
  }

  function seatRowNumber(seatId) {
    var m = /^(\d+)/.exec(String(seatId));
    return m ? parseInt(m[1], 10) : 0;
  }

  /** Row 1 = priority only. Rows 9 & 22 = extra legroom (first row under each toilet block in the map). */
  function applySeatKindClasses(btn, seatId) {
    var row = seatRowNumber(seatId);
    if (row === 1) {
      btn.classList.add('seat-booking__seat--priority');
      return;
    }
    if (row === 9 || row === 22) {
      btn.classList.add('seat-booking__seat--extra');
      return;
    }
    btn.classList.add('seat-booking__seat--regular');
  }

  function clearSeatFromOthers(map, journeyKey, legIndex, seatId, exceptSlot) {
    var leg = map[journeyKey] && map[journeyKey][String(legIndex)];
    if (!leg) return;
    Object.keys(leg).forEach(function (slotStr) {
      if (parseInt(slotStr, 10) === exceptSlot) return;
      if (leg[slotStr] === seatId) delete leg[slotStr];
    });
  }

  function countMissingAssignments(map, journeysList, paxCount) {
    var missing = 0;
    for (var ji = 0; ji < journeysList.length; ji++) {
      var j = journeysList[ji];
      if (j.isLightFare) continue;
      for (var li = 0; li < j.legs.length; li++) {
        var leg = j.legs[li];
        var legIdx = leg.index;
        for (var s = 1; s <= paxCount; s++) {
          var legMap = map[j.key] && map[j.key][String(legIdx)];
          var picked = legMap && legMap[String(s)];
          if (!picked) missing++;
        }
      }
    }
    return missing;
  }

  function findOccupant(map, journeyKey, legIndex, seatId) {
    var leg = map[journeyKey] && map[journeyKey][String(legIndex)];
    if (!leg) return 0;
    var slotStr;
    for (slotStr in leg) {
      if (leg[slotStr] === seatId) return parseInt(slotStr, 10);
    }
    return 0;
  }

  function init() {
    var cfgEl = document.getElementById('seat-booking-config');
    if (!cfgEl || !cfgEl.dataset.journeysB64) return;

    var journeys;
    try {
      journeys = JSON.parse(decodeB64Utf8(cfgEl.dataset.journeysB64));
    } catch (e) {
      return;
    }
    if (!journeys || !journeys.length) return;

    var seatMap = loadSeatMap();
    var journeyKey = journeys[0].key;
    var legIndex = 0;
    var selectedPax = 1;

    var legTabsEl = document.querySelector('[data-leg-tabs]');
    var journeyPickers = document.querySelectorAll('[data-summary-picker][data-trip-key]');
    var paxButtons = document.querySelectorAll('[data-pax-slot]');
    var seatGrid = document.querySelector('[data-seat-grid]');
    var seatButtons = document.querySelectorAll('[data-seat-id]');
    var seatRows = document.querySelectorAll('[data-row-index]');
    var continueBtn = document.getElementById('seat-continue-placeholder');

    if (!legTabsEl || !seatGrid) return;

    function currentJourney() {
      for (var i = 0; i < journeys.length; i++) {
        if (journeys[i].key === journeyKey) return journeys[i];
      }
      return journeys[0];
    }

    function updateJourneyPickersUI() {
      journeyPickers.forEach(function (btn) {
        var active = btn.getAttribute('data-trip-key') === journeyKey;
        btn.classList.toggle('seat-booking__summary-btn--active', active);
        if (active) btn.setAttribute('aria-current', 'true');
        else btn.removeAttribute('aria-current');
      });
    }

    function updateContinueHref() {
      if (!continueBtn) return;
      var baseHref = continueBtn.getAttribute('data-base-href') || continueBtn.getAttribute('href') || '#';
      if (baseHref === '#') return;
      try {
        var url = new URL(baseHref, window.location.origin);
        var encoded = toBase64UrlUtf8(JSON.stringify(seatMap));
        if (encoded) url.searchParams.set('seatSel', encoded);
        try {
          var rawPax = sessionStorage.getItem(STORAGE_PAX);
          if (rawPax) url.searchParams.set('paxSel', toBase64UrlUtf8(rawPax));
        } catch (ep) {}
        continueBtn.setAttribute('href', url.pathname + url.search);
      } catch (e) {}
    }

    function buildLegTabs() {
      var j = currentJourney();
      if (j.legs.length <= 1) {
        legTabsEl.hidden = true;
        legTabsEl.innerHTML = '';
        return;
      }
      legTabsEl.hidden = false;
      legTabsEl.innerHTML = '';
      j.legs.forEach(function (leg) {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'seat-booking__leg-tab';
        btn.setAttribute('role', 'tab');
        btn.setAttribute('data-leg-index', String(leg.index));
        btn.textContent = leg.legLabel;
        var isActive = leg.index === legIndex;
        if (isActive) btn.classList.add('seat-booking__leg-tab--active');
        btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
        legTabsEl.appendChild(btn);
      });
    }

    function applyCabinLayout() {
      var hasBusiness = !!currentJourney().hasBusinessCabin;
      seatRows.forEach(function (rowEl) {
        var idx = parseInt(rowEl.getAttribute('data-row-index') || '0', 10);
        var isBusiness = hasBusiness && idx > 0 && idx <= 10;
        rowEl.classList.toggle('seat-booking__row--business', isBusiness);
        rowEl.classList.toggle('seat-booking__row--economy', !isBusiness);
      });
    }

    function paintSeats() {
      seatButtons.forEach(function (btn) {
        var id = btn.getAttribute('data-seat-id');
        btn.classList.remove(
          'seat-booking__seat--extra',
          'seat-booking__seat--priority',
          'seat-booking__seat--regular',
          'seat-booking__seat--unavailable',
          'seat-booking__seat--assigned',
          'seat-booking__seat--mine',
        );
        btn.disabled = false;

        var letter = btn.getAttribute('data-seat-letter') || String(id).slice(-1);

        if (hashUnavailable(journeyKey, legIndex, id)) {
          btn.classList.add('seat-booking__seat--unavailable');
          btn.disabled = true;
          btn.textContent = letter;
          btn.setAttribute('aria-label', 'Seat ' + id + ', unavailable');
          return;
        }

        var occ = findOccupant(seatMap, journeyKey, legIndex, id);
        if (occ) {
          btn.textContent = 'P' + occ;
          btn.setAttribute(
            'aria-label',
            'Seat ' + id + (occ === selectedPax ? ', your seat' : ', passenger ' + occ),
          );
          btn.classList.add('seat-booking__seat--assigned');
          if (occ === selectedPax) btn.classList.add('seat-booking__seat--mine');
        } else {
          applySeatKindClasses(btn, id);
          btn.textContent = letter;
          btn.setAttribute('aria-label', 'Seat ' + id);
        }
      });
    }

    function syncPaxPanel() {
      paxButtons.forEach(function (btn) {
        var slot = parseInt(btn.getAttribute('data-pax-slot'), 10);
        var seatLine = btn.querySelector('[data-pax-seat-line]');
        var leg = seatMap[journeyKey] && seatMap[journeyKey][String(legIndex)];
        var seatId = leg && leg[String(slot)] ? leg[String(slot)] : '';
        if (seatLine) seatLine.textContent = seatId ? seatId : 'Not selected';
        btn.classList.toggle('seat-booking__pax-card--selected', slot === selectedPax);
      });
    }

    function applyPaxNames() {
      var bySlot = {};
      try {
        var raw = sessionStorage.getItem(STORAGE_PAX);
        if (raw) {
          var list = JSON.parse(raw);
          if (Array.isArray(list)) {
            list.forEach(function (entry) {
              if (entry && entry.slot) {
                bySlot[entry.slot] = (entry.displayName || '').trim();
              }
            });
          }
        }
      } catch (e) {}
      paxButtons.forEach(function (btn) {
        var slot = parseInt(btn.getAttribute('data-pax-slot'), 10);
        var card = document.getElementById('seat-pax-' + slot);
        var nameEl = card && card.querySelector('[data-pax-name]');
        if (!nameEl) return;
        var name = bySlot[slot];
        nameEl.textContent = name ? name : 'Passenger ' + slot;
      });
    }

    legTabsEl.addEventListener('click', function (e) {
      var t = e.target.closest('[data-leg-index]');
      if (!t) return;
      legIndex = parseInt(t.getAttribute('data-leg-index'), 10);
      buildLegTabs();
      paintSeats();
      syncPaxPanel();
    });

    journeyPickers.forEach(function (picker) {
      picker.addEventListener('click', function () {
        if (picker.classList.contains('seat-booking__summary-btn--solo')) return;
        journeyKey = picker.getAttribute('data-trip-key');
        legIndex = 0;
        updateJourneyPickersUI();
        buildLegTabs();
        applyCabinLayout();
        paintSeats();
        syncPaxPanel();
        updateContinueHref();
      });
    });
    var continueDialog = document.getElementById('seat-continue-dialog');
    var dialogBack = document.getElementById('seat-dialog-back');
    var dialogConfirm = document.getElementById('seat-dialog-confirm');

    if (continueBtn) {
      continueBtn.addEventListener('click', function (e) {
        saveSeatMap(seatMap);
        updateContinueHref();
        var paxCount = paxButtons.length;
        var missing = countMissingAssignments(seatMap, journeys, paxCount);
        if (missing > 0 && continueDialog && typeof continueDialog.showModal === 'function') {
          e.preventDefault();
          continueDialog.showModal();
        }
      });
    }

    if (dialogBack && continueDialog) {
      dialogBack.addEventListener('click', function () {
        continueDialog.close();
      });
    }

    if (dialogConfirm && continueBtn && continueDialog) {
      dialogConfirm.addEventListener('click', function () {
        saveSeatMap(seatMap);
        updateContinueHref();
        continueDialog.close();
        var href = continueBtn.getAttribute('href');
        if (href && href !== '#') window.location.href = href;
      });
    }

    window.addEventListener('beforeunload', function () {
      saveSeatMap(seatMap);
    });

    seatGrid.addEventListener('click', function (e) {
      var btn = e.target.closest('[data-seat-id]');
      if (!btn || btn.disabled) return;
      var seatId = btn.getAttribute('data-seat-id');
      if (!seatMap[journeyKey]) seatMap[journeyKey] = {};
      if (!seatMap[journeyKey][String(legIndex)]) seatMap[journeyKey][String(legIndex)] = {};
      var leg = seatMap[journeyKey][String(legIndex)];
      if (leg[String(selectedPax)] === seatId) {
        delete leg[String(selectedPax)];
        if (Object.keys(leg).length === 0) delete seatMap[journeyKey][String(legIndex)];
        if (Object.keys(seatMap[journeyKey]).length === 0) delete seatMap[journeyKey];
      } else {
        clearSeatFromOthers(seatMap, journeyKey, legIndex, seatId, selectedPax);
        leg[String(selectedPax)] = seatId;
      }
      saveSeatMap(seatMap);
      paintSeats();
      syncPaxPanel();
      updateContinueHref();
    });

    paxButtons.forEach(function (btn) {
      btn.addEventListener('click', function () {
        selectedPax = parseInt(btn.getAttribute('data-pax-slot'), 10);
        syncPaxPanel();
        paintSeats();
        updateContinueHref();
      });
    });

    applyPaxNames();
    updateJourneyPickersUI();
    buildLegTabs();
    applyCabinLayout();
    paintSeats();
    syncPaxPanel();
    updateContinueHref();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
