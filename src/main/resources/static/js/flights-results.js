/**
 * Search results: fare panel open/close, fare selection animation, route-details close animation,
 * and navigation to review/inbound pages.
 */
(function () {
  'use strict';

  /** Matches fare-plan-chosen-tap-and-leave (1.04s) + short buffer before navigation. */
  let FARE_SELECT_EXIT_MS = 1040;

  function navigateDelayMs() {
    return FARE_SELECT_EXIT_MS + 60;
  }

  function prefersReducedMotion() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function setQueryParamIfPresent(params, key, value) {
    let trimmedValue = (value || '').trim();
    if (trimmedValue) params.set(key, trimmedValue);
  }

  function setDatasetQueryParam(params, key, card, datasetKey) {
    setQueryParamIfPresent(params, key, card.dataset[datasetKey]);
  }

  function addPassengerSearchParams(params, card) {
    setDatasetQueryParam(params, 'cabinClass', card, 'cabin');
    setDatasetQueryParam(params, 'adults', card, 'adults');
    setDatasetQueryParam(params, 'children', card, 'children');
  }

  function addSegmentSnapshotParams(params, card) {
    setDatasetQueryParam(params, 'segDep', card, 'cardDepartTime');
    setDatasetQueryParam(params, 'segArr', card, 'cardArrivalTime');
    setDatasetQueryParam(params, 'segDur', card, 'cardDuration');
    setDatasetQueryParam(params, 'segFlights', card, 'cardFlights');
    let arrivalPlusDays = (card.dataset.cardArrPlus || '').trim();
    if (arrivalPlusDays !== '') params.set('segArrPlus', arrivalPlusDays);
    setDatasetQueryParam(params, 'segOrig', card, 'cardOriginCode');
    setDatasetQueryParam(params, 'segDest', card, 'cardDestCode');
  }

  function addInboundCarryParams(params, card) {
    setDatasetQueryParam(params, 'obFrom', card, 'obFrom');
    setDatasetQueryParam(params, 'obTo', card, 'obTo');
    setDatasetQueryParam(params, 'obDepart', card, 'obDepart');
    setDatasetQueryParam(params, 'outboundPrice', card, 'outboundPrice');
    setDatasetQueryParam(params, 'obFlight', card, 'obFlightId');
    setDatasetQueryParam(params, 'obFare', card, 'obFare');
  }

  /**
   * Clears selection-exit state so fare columns are visible again. Needed when the user returns via
   * the back button (bfcache restores the frozen DOM with animation-fill forwards opacity: 0).
   */
  function resetFareSelectionUi() {
    document.querySelectorAll('.fare-grid.fare-grid--select-busy').forEach(function (g) {
      g.classList.remove('fare-grid--select-busy');
    });
    document
      .querySelectorAll(
        '.fare-plan.fare-plan--peer-out, .fare-plan.fare-plan--chosen, .fare-plan.fare-plan--chosen-exit',
      )
      .forEach(function (el) {
        el.classList.remove(
          'fare-plan--peer-out',
          'fare-plan--chosen',
          'fare-plan--chosen-exit',
        );
      });
  }

  function setFarePanelOpen(panel, btn, open) {
    let inner = panel.querySelector('.flight-card__fares-inner');
    if (open) {
      panel.dataset.state = 'open';
      panel.setAttribute('aria-hidden', 'false');
      if (inner) inner.removeAttribute('inert');
      if (btn) btn.setAttribute('aria-expanded', 'true');
    } else {
      panel.dataset.state = 'closed';
      panel.setAttribute('aria-hidden', 'true');
      if (inner) inner.setAttribute('inert', '');
      if (btn) btn.setAttribute('aria-expanded', 'false');
    }
  }

  function initFarePanels() {
    const flightCards = document.querySelectorAll('[data-flight-card]');
    if (!flightCards.length) return;

    flightCards.forEach(function (card) {
      let btn = card.querySelector('[data-fare-toggle]');
      let panel = card.querySelector('[data-fare-panel]');
      if (!btn || !panel) return;

      btn.addEventListener('click', function () {
        let willOpen = panel.dataset.state !== 'open';
        flightCards.forEach(function (other) {
          let p = other.querySelector('[data-fare-panel]');
          let b = other.querySelector('[data-fare-toggle]');
          if (!p) return;
          if (p !== panel) setFarePanelOpen(p, b, false);
        });
        if (willOpen) {
          setFarePanelOpen(panel, btn, true);
        } else {
          setFarePanelOpen(panel, btn, false);
        }
      });
    });
  }

  /** Shared query for /book/review and /book/passengers (segment snapshot from the card). */
  function buildBookingQueryParams(card, tier, price) {
    let p = new URLSearchParams();
    p.set('from', card.dataset.searchFrom || '');
    p.set('to', card.dataset.searchTo || '');
    p.set('depart', card.dataset.searchDepart || '');
    p.set('trip', (card.dataset.trip || '').trim() || 'one-way');
    setDatasetQueryParam(p, 'return', card, 'searchReturn');
    addPassengerSearchParams(p, card);
    let searchLeg = (card.dataset.searchLeg || '').trim();
    if (searchLeg) p.set('leg', searchLeg);
    if (searchLeg === 'inbound') addInboundCarryParams(p, card);
    p.set('fare', tier);
    setDatasetQueryParam(p, 'flight', card, 'flightId');
    if (price) p.set('price', String(price));
    addSegmentSnapshotParams(p, card);
    return p;
  }

  /** Step 2: review flight + fare before passenger details. */
  function buildReviewUrl(card, tier, price) {
    let p = buildBookingQueryParams(card, tier, price);
    p.set('highlight', '1');
    return '/book/review?' + p.toString();
  }

  /** Return leg: same UI as outbound results with swapped city pair and return date as depart. */
  function buildInboundSearchUrl(card, tier, price) {
    let p = new URLSearchParams();
    p.set('from', card.dataset.searchTo || '');
    p.set('to', card.dataset.searchFrom || '');
    p.set('depart', (card.dataset.searchReturn || '').trim());
    p.set('trip', 'return');
    p.set('return', '');
    p.set('leg', 'inbound');
    addPassengerSearchParams(p, card);
    p.set('obFrom', card.dataset.searchFrom || '');
    p.set('obTo', card.dataset.searchTo || '');
    p.set('obDepart', card.dataset.searchDepart || '');
    p.set('fare', tier);
    let fid = (card.dataset.flightId || '').trim();
    if (fid) {
      p.set('flight', fid);
      p.set('obFlight', fid);
    }
    if (tier) p.set('obFare', tier);
    if (price) p.set('outboundPrice', String(price));
    return '/search-flights?' + p.toString();
  }

  function navigateAfterFareSelect(card, btn) {
    let tier = btn.getAttribute('data-fare-tier') || '';
    let price = btn.getAttribute('data-fare-price') || '';
    let trip = (card.dataset.trip || '').trim();
    let hasReturnDate = !!(card.dataset.searchReturn || '').trim();
    let url;
    let searchLeg = (card.dataset.searchLeg || '').trim();
    if (trip === 'return' && hasReturnDate && searchLeg !== 'inbound') {
      url = buildInboundSearchUrl(card, tier, price);
    } else {
      url = buildReviewUrl(card, tier, price);
    }
    window.location.assign(url);
  }

  function initFareSelect() {
    document.addEventListener('click', function (e) {
      let btn = e.target.closest('[data-fare-select]');
      if (!btn) return;
      let card = btn.closest('[data-flight-card]');
      if (!card) return;
      let grid = btn.closest('.fare-grid');
      if (!grid) return;
      e.preventDefault();

      if (prefersReducedMotion()) {
        navigateAfterFareSelect(card, btn);
        return;
      }

      let plan = btn.closest('.fare-plan');
      if (!plan || grid.classList.contains('fare-grid--select-busy')) return;
      grid.classList.add('fare-grid--select-busy');

      plan.classList.add('fare-plan--chosen');

      let plans = grid.querySelectorAll(':scope > .fare-plan');
      plans.forEach(function (fp) {
        if (fp !== plan) fp.classList.add('fare-plan--peer-out');
      });

      plan.classList.add('fare-plan--chosen-exit');

      window.setTimeout(function () {
        navigateAfterFareSelect(card, btn);
      }, navigateDelayMs());
    });
  }

  /**
   * When retracting Route details, run a short fade-out + lift before removing [open]
   * so it matches the open animation. Opening stays native (CSS only).
   */
  function initRouteDetailsCloseAnimation() {
    let detailsEls = document.querySelectorAll('.flight-card__details[data-route-details]');
    if (!detailsEls.length) return;

    detailsEls.forEach(function (details) {
      let summary = details.querySelector('summary.flight-card__details-summary');
      if (!summary) return;

      summary.addEventListener(
        'click',
        function (e) {
          if (!details.open) return;

          if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            return;
          }

          if (details.classList.contains('flight-card__details--closing')) {
            e.preventDefault();
            e.stopPropagation();
            return;
          }

          e.preventDefault();
          e.stopPropagation();

          let body = details.querySelector('.flight-card__details-body');
          if (!body) {
            details.removeAttribute('open');
            return;
          }

          let finished = false;

          function complete() {
            if (finished) return;
            finished = true;
            body.removeEventListener('animationend', onAnimationEnd);
            details.removeAttribute('open');
            details.classList.remove('flight-card__details--closing');
          }

          function onAnimationEnd(ev) {
            if (ev.target !== body) return;
            if (ev.animationName !== 'flight-route-details-close') return;
            complete();
          }

          details.classList.add('flight-card__details--closing');
          body.addEventListener('animationend', onAnimationEnd);
          window.setTimeout(complete, 450);
        },
        true,
      );
    });
  }

  /** Title comboboxes on `/book/passengers` (homepage-style white trigger; options Mr / Miss only). */
  function initPassengerTitleCombos() {
    let roots = document.querySelectorAll('[data-bp-title-combo]');
    if (!roots.length) return;

    function closeAll() {
      roots.forEach(function (root) {
        let list = root.querySelector('.bp-wf-title-menu');
        let trigger = root.querySelector('.bp-wf-combo-trigger');
        if (list) list.classList.remove('is-open');
        if (trigger) trigger.setAttribute('aria-expanded', 'false');
      });
    }

    document.addEventListener(
      'click',
      function (e) {
        if (e.target.closest('[data-bp-title-combo]')) return;
        closeAll();
      },
      true,
    );

    roots.forEach(function (root) {
      let hidden = root.querySelector('input[type="hidden"]');
      let trigger = root.querySelector('.bp-wf-combo-trigger');
      let list = root.querySelector('.bp-wf-title-menu');
      if (!hidden || !trigger || !list) return;

      function syncLabel() {
        let v = (hidden.value || '').trim();
        trigger.textContent = v || 'Title';
      }

      trigger.addEventListener('click', function (e) {
        e.preventDefault();
        let opening = !list.classList.contains('is-open');
        closeAll();
        if (opening) {
          list.classList.add('is-open');
          trigger.setAttribute('aria-expanded', 'true');
        }
      });

      list.querySelectorAll('.trip-option').forEach(function (btn) {
        btn.addEventListener('click', function (e) {
          e.preventDefault();
          hidden.value = btn.getAttribute('data-value') || '';
          syncLabel();
          closeAll();
          hidden.dispatchEvent(new Event('input', { bubbles: true }));
        });
      });

      syncLabel();
    });
  }

  /**
   * `/book/passengers`: restrict name fields (letters + space/hyphen/apostrophe), dial code (+ fixed + digits),
   * numeric phone, and email must contain '@'.
   */
  function initPassengerFieldConstraints() {
    let form = document.querySelector('[data-bp-passenger-form]');
    if (!form) return;

    let nameStripRe = /[^\p{L}\s'-]/gu;
    let nameFallbackRe = /[^A-Za-z\u00C0-\u024F\s'-]/g;

    function sanitizeNameValue(raw) {
      let s = String(raw || '');
      try {
        return s.replace(nameStripRe, '');
      } catch (e) {
        return s.replace(nameFallbackRe, '');
      }
    }

    function attachNameField(el) {
      function apply() {
        let next = sanitizeNameValue(el.value);
        if (el.value !== next) el.value = next;
      }
      el.addEventListener('input', apply);
      el.addEventListener('blur', apply);
      el.addEventListener('paste', function (e) {
        let text = (e.clipboardData || window.clipboardData || {}).getData('text') || '';
        if (!text) return;
        e.preventDefault();
        let merged =
          el.value.slice(0, el.selectionStart || 0) +
          text +
          el.value.slice(el.selectionEnd || 0);
        el.value = sanitizeNameValue(merged);
      });
      apply();
    }

    form.querySelectorAll('.bp-wf-input--pax-name').forEach(attachNameField);

    let dialVisible = document.getElementById('bp-contact-dial');
    let dialHidden = document.getElementById('bp-contact-dial-hidden');

    function syncDialHidden() {
      if (!dialVisible || !dialHidden) return;
      let digits = String(dialVisible.value || '').replace(/\D/g, '');
      dialVisible.value = digits;
      dialHidden.value = digits.length ? '+' + digits : '+';
    }

    if (dialVisible && dialHidden) {
      dialVisible.addEventListener('input', syncDialHidden);
      dialVisible.addEventListener('blur', syncDialHidden);
      dialVisible.addEventListener('paste', function (e) {
        let text = (e.clipboardData || window.clipboardData || {}).getData('text') || '';
        if (!text) return;
        e.preventDefault();
        let merged =
          dialVisible.value.slice(0, dialVisible.selectionStart || 0) +
          text +
          dialVisible.value.slice(dialVisible.selectionEnd || 0);
        dialVisible.value = merged.replace(/\D/g, '');
        syncDialHidden();
      });
      window.setTimeout(syncDialHidden, 0);
      window.setTimeout(syncDialHidden, 120);
    }

    let phoneEl = document.getElementById('bp-contact-phone');
    function syncPhoneDigits() {
      if (!phoneEl) return;
      let digits = String(phoneEl.value || '').replace(/\D/g, '');
      if (phoneEl.value !== digits) phoneEl.value = digits;
    }
    if (phoneEl) {
      phoneEl.addEventListener('input', syncPhoneDigits);
      phoneEl.addEventListener('blur', syncPhoneDigits);
      phoneEl.addEventListener('paste', function (e) {
        let text = (e.clipboardData || window.clipboardData || {}).getData('text') || '';
        if (!text) return;
        e.preventDefault();
        let merged =
          phoneEl.value.slice(0, phoneEl.selectionStart || 0) +
          text +
          phoneEl.value.slice(phoneEl.selectionEnd || 0);
        phoneEl.value = merged.replace(/\D/g, '');
      });
    }

    let emailEl = document.getElementById('bp-contact-email');

    function clearPassengerInlineErrors() {
      form.querySelectorAll('.bp-wf-inline-error').forEach(function (el) {
        el.textContent = '';
        el.setAttribute('hidden', '');
      });
      form.querySelectorAll('[aria-invalid="true"]').forEach(function (el) {
        el.removeAttribute('aria-invalid');
      });
    }

    form.addEventListener('input', clearPassengerInlineErrors);
    form.addEventListener('change', clearPassengerInlineErrors);

    function showPassengerInlineError(errEl, msg, focusEl) {
      if (errEl) {
        errEl.textContent = msg;
        errEl.removeAttribute('hidden');
      }
      if (focusEl && focusEl.setAttribute) focusEl.setAttribute('aria-invalid', 'true');
      if (focusEl && focusEl.focus) focusEl.focus();
    }

    /**
     * Validates in document order; only the first failure is shown (under that field’s white card).
     */
    function validatePassengerFormForSubmit() {
      clearPassengerInlineErrors();
      if (emailEl) emailEl.setCustomValidity('');
      if (dialVisible && dialHidden) syncDialHidden();
      syncPhoneDigits();

      let errDial = document.getElementById('bp-err-contact-dial');
      let errPhone = document.getElementById('bp-err-contact-phone');
      let errEmail = document.getElementById('bp-err-contact-email');

      let checks = [];

      form.querySelectorAll(':scope > fieldset').forEach(function (fs) {
        let hidden = fs.querySelector('input[type="hidden"][name$="_title"]');
        if (!hidden || !hidden.name) return;
        let m = hidden.name.match(/^pax_(\d+)_title$/);
        if (!m) return;
        let slot = m[1];
        let trigger = document.getElementById('bp-title-trigger-' + slot);
        let given = document.getElementById('bp-given-' + slot);
        let family = document.getElementById('bp-family-' + slot);
        let errTitle = document.getElementById('bp-err-pax-' + slot + '-title');
        let errGiven = document.getElementById('bp-err-pax-' + slot + '-given');
        let errFamily = document.getElementById('bp-err-pax-' + slot + '-family');

        checks.push({
          test: function () {
            return (hidden.value || '').trim();
          },
          errEl: errTitle,
          focusEl: trigger,
          msg: 'Please choose a title',
        });
        checks.push({
          test: function () {
            return given && (given.value || '').trim();
          },
          errEl: errGiven,
          focusEl: given,
          msg: 'First name cannot be blank',
        });
        checks.push({
          test: function () {
            return family && (family.value || '').trim();
          },
          errEl: errFamily,
          focusEl: family,
          msg: 'Surname cannot be blank',
        });
      });

      checks.push({
        test: function () {
          return dialHidden && dialHidden.value && dialHidden.value !== '+';
        },
        errEl: errDial,
        focusEl: dialVisible,
        msg: 'Country code cannot be blank',
      });
      checks.push({
        test: function () {
          return phoneEl && String(phoneEl.value || '').replace(/\D/g, '').length > 0;
        },
        errEl: errPhone,
        focusEl: phoneEl,
        msg: 'Phone number cannot be blank',
      });
      checks.push({
        test: function () {
          return emailEl && (emailEl.value || '').trim();
        },
        errEl: errEmail,
        focusEl: emailEl,
        msg: 'Email cannot be blank',
      });
      checks.push({
        test: function () {
          return emailEl && String(emailEl.value || '').trim().indexOf('@') !== -1;
        },
        errEl: errEmail,
        focusEl: emailEl,
        msg: "Email must contain an '@'",
      });

      for (let i = 0; i < checks.length; i++) {
        let c = checks[i];
        if (!c.test()) {
          showPassengerInlineError(c.errEl, c.msg, c.focusEl);
          return { ok: false, focusEl: c.focusEl };
        }
      }
      return { ok: true, focusEl: null };
    }

    form.addEventListener('submit', function (e) {
      let r = validatePassengerFormForSubmit();
      if (!r.ok) e.preventDefault();
    });

    let continueLink = form.querySelector('[data-bp-passenger-continue]');
    if (continueLink) {
      continueLink.addEventListener('click', function (e) {
        let r = validatePassengerFormForSubmit();
        if (!r.ok) {
          e.preventDefault();
          return;
        }
        try {
          let pax = [];
          form.querySelectorAll(':scope > fieldset').forEach(function (fs) {
            let hidden = fs.querySelector('input[type="hidden"][name$="_title"]');
            if (!hidden || !hidden.name) return;
            let m = hidden.name.match(/^pax_(\d+)_title$/);
            if (!m) return;
            let slot = m[1];
            let title = (document.getElementById('bp-title-val-' + slot) || {}).value || '';
            let given = (document.getElementById('bp-given-' + slot) || {}).value || '';
            let family = (document.getElementById('bp-family-' + slot) || {}).value || '';
            let displayName = (title + ' ' + given + ' ' + family).replace(/\s+/g, ' ').trim();
            pax.push({ slot: parseInt(slot, 10), displayName: displayName });
          });
          sessionStorage.setItem('glideBookingPaxNames', JSON.stringify(pax));
          let encoded = btoa(unescape(encodeURIComponent(JSON.stringify(pax))))
            .replace(/\+/g, '-')
            .replace(/\//g, '_')
            .replace(/=+$/g, '');
          if (encoded) {
            let u = new URL(continueLink.getAttribute('href'), window.location.origin);
            u.searchParams.set('paxSel', encoded);
            continueLink.setAttribute('href', u.pathname + u.search);
          }
        } catch (err) {}
      });
    }
  }

  function init() {
    resetFareSelectionUi();
    initFarePanels();
    initFareSelect();
    initRouteDetailsCloseAnimation();
    initPassengerTitleCombos();
    initPassengerFieldConstraints();
  }

  window.addEventListener('pageshow', function () {
    resetFareSelectionUi();
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
