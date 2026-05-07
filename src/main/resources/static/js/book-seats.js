/**
 * /book/seats — journey / leg switching, passenger selection, and seat assignment (sessionStorage).
 * Colours come from CSS variables in base.css (see seats.css).
 */
(function () {
  'use strict';

  let STORAGE_ASSIGN = 'glideSeatAssignmentsV1';
  let STORAGE_ASSIGN_FALLBACK = 'glideSeatAssignmentsV1Local';
  let STORAGE_PAX = 'glideBookingPaxNames';
  let STORAGE_SEAT_RESET_ON_LOAD = 'glideSeatResetOnNextLoad';

  function decodeB64Utf8(b64) {
    let binary = atob(b64);
    let bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return new TextDecoder('utf-8').decode(bytes);
  }

  function loadSeatMap() {
    try {
      let raw = sessionStorage.getItem(STORAGE_ASSIGN) || localStorage.getItem(STORAGE_ASSIGN_FALLBACK);
      if (!raw) return {};
      let o = JSON.parse(raw);
      return typeof o === 'object' && o !== null ? o : {};
    } catch (e) {
      return {};
    }
  }

  function saveSeatMap(map) {
    try {
      let encoded = JSON.stringify(map);
      sessionStorage.setItem(STORAGE_ASSIGN, encoded);
      localStorage.setItem(STORAGE_ASSIGN_FALLBACK, encoded);
    } catch (e) {}
  }

  function clearSavedSeatMap() {
    try {
      sessionStorage.removeItem(STORAGE_ASSIGN);
      localStorage.removeItem(STORAGE_ASSIGN_FALLBACK);
    } catch (e) {}
  }

  function toBase64UrlUtf8(text) {
    let bytes = new TextEncoder().encode(text);
    let binary = '';
    bytes.forEach(function (byte) {
      binary += String.fromCharCode(byte);
    });
    let encoded = btoa(binary);
    return encoded.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  }

  function hashUnavailable(journeyKey, legIndex, seatId) {
    let s = journeyKey + '|' + legIndex + '|' + seatId;
    let h = 2166136261;
    for (let i = 0; i < s.length; i++) {
      h ^= s.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return Math.abs(h) % 10 === 0;
  }

  function seatRowNumber(seatId) {
    let m = /^(\d+)/.exec(String(seatId));
    return m ? parseInt(m[1], 10) : 0;
  }

  /** Economy: row 11 priority; rows 19 and 32 extra legroom. Business: no extra-legroom classing. */
  function applySeatKindClasses(btn, seatId, isBusinessCabin) {
    let row = seatRowNumber(seatId);
    if (isBusinessCabin) {
      btn.classList.add('seat-booking__seat--regular');
      return;
    }
    if (row === 11) {
      btn.classList.add('seat-booking__seat--priority');
      return;
    }
    if (row === 19 || row === 32) {
      btn.classList.add('seat-booking__seat--extra');
      return;
    }
    btn.classList.add('seat-booking__seat--regular');
  }

  function clearSeatFromOthers(map, journeyKey, legIndex, seatId, exceptSlot) {
    let leg = map[journeyKey] && map[journeyKey][String(legIndex)];
    if (!leg) return;
    Object.keys(leg).forEach(function (slotStr) {
      if (parseInt(slotStr, 10) === exceptSlot) return;
      if (leg[slotStr] === seatId) delete leg[slotStr];
    });
  }

  function countMissingAssignments(map, journeysList, paxCount) {
    let missing = 0;
    for (let ji = 0; ji < journeysList.length; ji++) {
      let j = journeysList[ji];
      if (j.isLightFare) continue;
      for (let li = 0; li < j.legs.length; li++) {
        let leg = j.legs[li];
        let legIdx = leg.index;
        for (let s = 1; s <= paxCount; s++) {
          let legMap = map[j.key] && map[j.key][String(legIdx)];
          let picked = legMap && legMap[String(s)];
          if (!picked) missing++;
        }
      }
    }
    return missing;
  }

  function findOccupant(map, journeyKey, legIndex, seatId) {
    let leg = map[journeyKey] && map[journeyKey][String(legIndex)];
    if (!leg) return 0;
    let slotStr;
    for (slotStr in leg) {
      if (leg[slotStr] === seatId) return parseInt(slotStr, 10);
    }
    return 0;
  }

  function unavailableKey(journeyKey, legIndex, seatId) {
    return journeyKey + '|' + String(legIndex) + '|' + seatId;
  }

  function postSeatAction(action, journeyKey, legIndex, seatId) {
    let body = new URLSearchParams();
    body.set('journeyKey', journeyKey);
    body.set('legIndex', String(legIndex));
    body.set('seatId', seatId);
    return fetch('/book/seats/' + action + window.location.search, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: body.toString(),
    });
  }

  function mapUnavailableSeats(payload) {
    let mapped = {};
    if (!payload || !Array.isArray(payload.seats)) return mapped;
    payload.seats.forEach(function (entry) {
      if (!entry) return;
      mapped[unavailableKey(entry.journeyKey, entry.legIndex, entry.seatId)] = true;
    });
    return mapped;
  }

  function init() {
    let cfgEl = document.getElementById('seat-booking-config');
    if (!cfgEl || !cfgEl.dataset.journeysB64) return;

    let journeys;
    try {
      journeys = JSON.parse(decodeB64Utf8(cfgEl.dataset.journeysB64));
    } catch (e) {
      return;
    }
    if (!journeys || !journeys.length) return;

    let resetFromPassengerStep = false;
    try {
      resetFromPassengerStep = sessionStorage.getItem(STORAGE_SEAT_RESET_ON_LOAD) === '1';
      if (resetFromPassengerStep) sessionStorage.removeItem(STORAGE_SEAT_RESET_ON_LOAD);
    } catch (e) {}

    if (resetFromPassengerStep) {
      clearSavedSeatMap();
    }

    let seatMap = loadSeatMap();
    let serverUnavailableSeats = {};
    let journeyKey = journeys[0].key;
    let legIndex = 0;
    let selectedPax = 1;

    let legTabsEl = document.querySelector('[data-leg-tabs]');
    let journeyPickers = document.querySelectorAll('[data-summary-picker][data-trip-key]');
    let paxButtons = document.querySelectorAll('[data-pax-slot]');
    let seatGrid = document.querySelector('[data-seat-grid]');
    let mapShell = document.querySelector('.seat-booking__map-shell');
    let seatButtons = document.querySelectorAll('[data-seat-id]');
    let seatRows = document.querySelectorAll('[data-row-index]');
    let breakRows = document.querySelectorAll('[data-seat-break-row]');
    let facilityRows = document.querySelectorAll('[data-seat-facility]');
    let wingRows = document.querySelectorAll('[data-seat-wing-row]');
    let extraInputs = document.querySelectorAll('[data-booking-extra]');
    let continueBtn = document.getElementById('seat-continue-placeholder');

    if (!legTabsEl || !seatGrid) return;

    function currentJourney() {
      for (let i = 0; i < journeys.length; i++) {
        if (journeys[i].key === journeyKey) return journeys[i];
      }
      return journeys[0];
    }

    function updateJourneyPickersUI() {
      journeyPickers.forEach(function (btn) {
        let active = btn.getAttribute('data-trip-key') === journeyKey;
        btn.classList.toggle('seat-booking__summary-btn--active', active);
        if (active) btn.setAttribute('aria-current', 'true');
        else btn.removeAttribute('aria-current');
      });
    }

    function updateContinueHref() {
      if (!continueBtn) return;
      let baseHref = continueBtn.getAttribute('data-base-href') || continueBtn.getAttribute('href') || '#';
      if (baseHref === '#') return;
      try {
        let url = new URL(baseHref, window.location.origin);
        let encoded = toBase64UrlUtf8(JSON.stringify(seatMap));
        if (encoded) url.searchParams.set('seatSel', encoded);
        try {
          let rawPax = sessionStorage.getItem(STORAGE_PAX);
          if (rawPax) url.searchParams.set('paxSel', toBase64UrlUtf8(rawPax));
        } catch (ep) {}
        let extras = [];
        extraInputs.forEach(function (input) {
          if (input.checked && input.value) extras.push(input.value);
        });
        if (extras.length) url.searchParams.set('extras', extras.join(','));
        else url.searchParams.delete('extras');
        continueBtn.setAttribute('href', url.pathname + url.search);
      } catch (e) {}
    }

    function buildLegTabs() {
      let j = currentJourney();
      if (j.legs.length <= 1) {
        legTabsEl.hidden = true;
        legTabsEl.innerHTML = '';
        return;
      }
      legTabsEl.hidden = false;
      legTabsEl.innerHTML = '';
      j.legs.forEach(function (leg) {
        let btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'seat-booking__leg-tab';
        btn.setAttribute('role', 'tab');
        btn.setAttribute('data-leg-index', String(leg.index));
        btn.textContent = leg.legLabel;
        let isActive = leg.index === legIndex;
        if (isActive) btn.classList.add('seat-booking__leg-tab--active');
        btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
        legTabsEl.appendChild(btn);
      });
    }

    function applyCabinLayout() {
      let hasBusiness = !!currentJourney().hasBusinessCabin;
      if (mapShell) {
        mapShell.classList.toggle('seat-booking__map-shell--business', hasBusiness);
      }
      seatRows.forEach(function (rowEl) {
        let idx = parseInt(rowEl.getAttribute('data-row-index') || '0', 10);
        let shownInBusiness = idx >= 1 && idx <= 10;
        let showRow = hasBusiness ? shownInBusiness : idx >= 1;
        rowEl.hidden = !showRow;
        rowEl.style.display = showRow ? '' : 'none';

        let displayRow = hasBusiness ? idx : idx + 10;
        let rowLabelEl = rowEl.querySelector('.seat-booking__row-label');
        if (rowLabelEl && showRow) rowLabelEl.textContent = String(displayRow);

        rowEl.classList.toggle('seat-booking__row--business', hasBusiness);
        rowEl.classList.toggle('seat-booking__row--economy', !hasBusiness);

        rowEl.querySelectorAll('[data-seat-letter]').forEach(function (seatBtn) {
          let letter = seatBtn.getAttribute('data-seat-letter') || '';
          let hideBusinessMiddle = hasBusiness && (letter === 'B' || letter === 'E');
          seatBtn.hidden = !showRow || hideBusinessMiddle;
          seatBtn.style.display = seatBtn.hidden ? 'none' : '';
          if (hideBusinessMiddle) {
            seatBtn.disabled = true;
            return;
          }
          if (!showRow) {
            seatBtn.disabled = true;
            return;
          }
          let seatId = String(displayRow) + letter;
          seatBtn.setAttribute('data-seat-id', seatId);
          seatBtn.setAttribute('aria-label', 'Seat ' + seatId);
        });
      });

      breakRows.forEach(function (breakEl) {
        let atRow = parseInt(breakEl.getAttribute('data-seat-break-row') || '0', 10);
        breakEl.hidden = hasBusiness || !(atRow === 9 || atRow === 22);
        breakEl.style.display = breakEl.hidden ? 'none' : '';
      });

      facilityRows.forEach(function (facilityEl) {
        if (!hasBusiness) {
          facilityEl.hidden = false;
          return;
        }
        let kind = facilityEl.getAttribute('data-seat-facility') || '';
        facilityEl.hidden = !(kind === 'top-exit' || kind === 'top-toilet' || kind === 'bottom-exit');
        facilityEl.style.display = facilityEl.hidden ? 'none' : '';
      });

      wingRows.forEach(function (wingEl) {
        wingEl.hidden = hasBusiness;
        wingEl.style.display = wingEl.hidden ? 'none' : '';
      });
    }

    function paintSeats() {
      let isBusinessCabin = !!currentJourney().hasBusinessCabin;
      seatButtons.forEach(function (btn) {
        if (btn.hidden) return;
        let id = btn.getAttribute('data-seat-id');
        btn.classList.remove(
          'seat-booking__seat--extra',
          'seat-booking__seat--priority',
          'seat-booking__seat--regular',
          'seat-booking__seat--unavailable',
          'seat-booking__seat--assigned',
          'seat-booking__seat--mine',
        );
        btn.disabled = false;

        let letter = btn.getAttribute('data-seat-letter') || String(id).slice(-1);

        if (hashUnavailable(journeyKey, legIndex, id)) {
          btn.classList.add('seat-booking__seat--unavailable');
          btn.disabled = true;
          btn.textContent = letter;
          btn.setAttribute('aria-label', 'Seat ' + id + ', unavailable');
          return;
        }

        if (serverUnavailableSeats[unavailableKey(journeyKey, legIndex, id)]) {
          btn.classList.add('seat-booking__seat--unavailable');
          btn.disabled = true;
          btn.textContent = letter;
          btn.setAttribute('aria-label', 'Seat ' + id + ', unavailable');
          return;
        }

        let occ = findOccupant(seatMap, journeyKey, legIndex, id);
        if (occ) {
          btn.textContent = 'P' + occ;
          btn.setAttribute(
            'aria-label',
            'Seat ' + id + (occ === selectedPax ? ', your seat' : ', passenger ' + occ),
          );
          btn.classList.add('seat-booking__seat--assigned');
          if (occ === selectedPax) btn.classList.add('seat-booking__seat--mine');
        } else {
          applySeatKindClasses(btn, id, isBusinessCabin);
          btn.textContent = letter;
          btn.setAttribute('aria-label', 'Seat ' + id);
        }
      });
    }

    function loadUnavailableSeats() {
      return fetch('/book/seats/unavailable' + window.location.search)
        .then(function (response) {
          if (!response.ok) return null;
          return response.json();
        })
        .then(function (payload) {
          serverUnavailableSeats = mapUnavailableSeats(payload);
          paintSeats();
          return serverUnavailableSeats;
        })
        .catch(function () {});
    }

    function validateSelectedSeats() {
      updateContinueHref();
      let href = continueBtn && continueBtn.getAttribute('href');
      let query = href && href.indexOf('?') !== -1 ? href.slice(href.indexOf('?')) : window.location.search;
      return fetch('/book/seats/validate' + query)
        .then(function (response) {
          if (!response.ok) return null;
          return response.json();
        })
        .then(function (payload) {
          return mapUnavailableSeats(payload);
        });
    }

    function selectedUnavailableKeys() {
      let blocked = [];
      Object.keys(seatMap).forEach(function (selectedJourneyKey) {
        Object.keys(seatMap[selectedJourneyKey] || {}).forEach(function (selectedLegIndex) {
          let leg = seatMap[selectedJourneyKey][selectedLegIndex] || {};
          Object.keys(leg).forEach(function (slot) {
            let seatId = leg[slot];
            let key = unavailableKey(selectedJourneyKey, selectedLegIndex, seatId);
            if (serverUnavailableSeats[key]) blocked.push(key);
          });
        });
      });
      return blocked;
    }

    function removeUnavailableSelections() {
      Object.keys(seatMap).forEach(function (selectedJourneyKey) {
        Object.keys(seatMap[selectedJourneyKey] || {}).forEach(function (selectedLegIndex) {
          let leg = seatMap[selectedJourneyKey][selectedLegIndex] || {};
          Object.keys(leg).forEach(function (slot) {
            let seatId = leg[slot];
            if (serverUnavailableSeats[unavailableKey(selectedJourneyKey, selectedLegIndex, seatId)]) {
              delete leg[slot];
            }
          });
          if (Object.keys(leg).length === 0) delete seatMap[selectedJourneyKey][selectedLegIndex];
        });
        if (Object.keys(seatMap[selectedJourneyKey] || {}).length === 0) delete seatMap[selectedJourneyKey];
      });
    }

    function showSeatUnavailableMessage() {
      if (!continueBtn || !continueBtn.parentNode) return;
      let existing = document.getElementById('seat-unavailable-error');
      if (existing) {
        existing.hidden = false;
        return;
      }
      let msg = document.createElement('p');
      msg.id = 'seat-unavailable-error';
      msg.className = 'flights-hero__hint';
      msg.setAttribute('role', 'alert');
      msg.textContent = 'One or more selected seats are no longer available. Please choose another seat.';
      continueBtn.insertAdjacentElement('afterend', msg);
    }

    function hideSeatUnavailableMessage() {
      let existing = document.getElementById('seat-unavailable-error');
      if (existing) existing.hidden = true;
    }

    function proceedToPayment() {
      saveSeatMap(seatMap);
      updateContinueHref();
      let href = continueBtn && continueBtn.getAttribute('href');
      if (href && href !== '#') window.location.href = href;
    }

    function continueAfterAvailabilityCheck(showMissingDialog) {
      if (!continueBtn) return;
      continueBtn.setAttribute('aria-busy', 'true');
      Promise.all([loadUnavailableSeats(), validateSelectedSeats()])
        .then(function (results) {
          continueBtn.removeAttribute('aria-busy');
          let selectedBlockedSeats = results[1] || {};
          Object.keys(selectedBlockedSeats).forEach(function (key) {
            serverUnavailableSeats[key] = true;
          });
          if (selectedUnavailableKeys().length > 0) {
            removeUnavailableSelections();
            saveSeatMap(seatMap);
            paintSeats();
            syncPaxPanel();
            updateContinueHref();
            showSeatUnavailableMessage();
            return;
          }
          hideSeatUnavailableMessage();
          let missing = countMissingAssignments(seatMap, journeys, paxButtons.length);
          if (missing > 0 && showMissingDialog && continueDialog && typeof continueDialog.showModal === 'function') {
            continueDialog.showModal();
            return;
          }
          proceedToPayment();
        })
        .catch(function () {
          continueBtn.removeAttribute('aria-busy');
        });
    }

    function syncPaxPanel() {
      paxButtons.forEach(function (btn) {
        let slot = parseInt(btn.getAttribute('data-pax-slot'), 10);
        let seatLine = btn.querySelector('[data-pax-seat-line]');
        let leg = seatMap[journeyKey] && seatMap[journeyKey][String(legIndex)];
        let seatId = leg && leg[String(slot)] ? leg[String(slot)] : '';
        if (seatLine) seatLine.textContent = seatId ? seatId : 'Not selected';
        btn.classList.toggle('seat-booking__pax-card--selected', slot === selectedPax);
      });
    }

    function applyPaxNames() {
      let bySlot = {};
      try {
        let raw = sessionStorage.getItem(STORAGE_PAX);
        if (raw) {
          let list = JSON.parse(raw);
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
        let slot = parseInt(btn.getAttribute('data-pax-slot'), 10);
        let card = document.getElementById('seat-pax-' + slot);
        let nameEl = card && card.querySelector('[data-pax-name]');
        if (!nameEl) return;
        let name = bySlot[slot];
        nameEl.textContent = name ? name : 'Passenger ' + slot;
      });
    }

    legTabsEl.addEventListener('click', function (e) {
      let t = e.target.closest('[data-leg-index]');
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
    let continueDialog = document.getElementById('seat-continue-dialog');
    let dialogBack = document.getElementById('seat-dialog-back');
    let dialogConfirm = document.getElementById('seat-dialog-confirm');

    if (continueBtn) {
      continueBtn.addEventListener('click', function (e) {
        e.preventDefault();
        continueAfterAvailabilityCheck(true);
      });
    }

    if (dialogBack && continueDialog) {
      dialogBack.addEventListener('click', function () {
        continueDialog.close();
      });
    }

    if (dialogConfirm && continueBtn && continueDialog) {
      dialogConfirm.addEventListener('click', function () {
        continueDialog.close();
        continueAfterAvailabilityCheck(false);
      });
    }

    window.addEventListener('beforeunload', function () {
      saveSeatMap(seatMap);
    });

    seatGrid.addEventListener('click', function (e) {
      let btn = e.target.closest('[data-seat-id]');
      if (!btn || btn.disabled) return;
      let seatId = btn.getAttribute('data-seat-id');
      if (!seatMap[journeyKey]) seatMap[journeyKey] = {};
      if (!seatMap[journeyKey][String(legIndex)]) seatMap[journeyKey][String(legIndex)] = {};
      let leg = seatMap[journeyKey][String(legIndex)];
      if (leg[String(selectedPax)] === seatId) {
        void postSeatAction('release', journeyKey, legIndex, seatId).finally(function () {
          delete leg[String(selectedPax)];
          if (Object.keys(leg).length === 0) delete seatMap[journeyKey][String(legIndex)];
          if (Object.keys(seatMap[journeyKey]).length === 0) delete seatMap[journeyKey];
          saveSeatMap(seatMap);
          paintSeats();
          syncPaxPanel();
          updateContinueHref();
          hideSeatUnavailableMessage();
        });
      } else {
        let previousSeat = leg[String(selectedPax)] || '';
        btn.disabled = true;
        void postSeatAction('hold', journeyKey, legIndex, seatId)
          .then(function (response) {
            if (!response.ok) throw new Error('seat-unavailable');
            if (previousSeat) {
              void postSeatAction('release', journeyKey, legIndex, previousSeat).catch(function () {});
            }
            clearSeatFromOthers(seatMap, journeyKey, legIndex, seatId, selectedPax);
            leg[String(selectedPax)] = seatId;
            saveSeatMap(seatMap);
            paintSeats();
            syncPaxPanel();
            updateContinueHref();
            hideSeatUnavailableMessage();
          })
          .catch(function () {
            serverUnavailableSeats[unavailableKey(journeyKey, legIndex, seatId)] = true;
            paintSeats();
            syncPaxPanel();
          });
      }
    });

    paxButtons.forEach(function (btn) {
      btn.addEventListener('click', function () {
        selectedPax = parseInt(btn.getAttribute('data-pax-slot'), 10);
        syncPaxPanel();
        paintSeats();
        updateContinueHref();
      });
    });

    extraInputs.forEach(function (input) {
      input.addEventListener('change', updateContinueHref);
    });

    applyPaxNames();
    updateJourneyPickersUI();
    buildLegTabs();
    applyCabinLayout();
    paintSeats();
    void loadUnavailableSeats();
    syncPaxPanel();
    updateContinueHref();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

