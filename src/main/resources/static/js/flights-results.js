/**
 * Search results: fare panel open/close, fare selection animation, route-details close animation,
 * and navigation to review/inbound pages.
 */
(function () {
  'use strict';

  /** Matches fare-plan-chosen-tap-and-leave (1.04s) + short buffer before navigation. */
  var FARE_SELECT_EXIT_MS = 1040;

  function navigateDelayMs() {
    return FARE_SELECT_EXIT_MS + 60;
  }

  function prefersReducedMotion() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
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
    var inner = panel.querySelector('.flight-card__fares-inner');
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
    var cards = document.querySelectorAll('[data-flight-card]');
    if (!cards.length) return;

    cards.forEach(function (card) {
      var btn = card.querySelector('[data-fare-toggle]');
      var panel = card.querySelector('[data-fare-panel]');
      if (!btn || !panel) return;

      btn.addEventListener('click', function () {
        var willOpen = panel.dataset.state !== 'open';
        cards.forEach(function (other) {
          var p = other.querySelector('[data-fare-panel]');
          var b = other.querySelector('[data-fare-toggle]');
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
    var p = new URLSearchParams();
    p.set('from', card.dataset.searchFrom || '');
    p.set('to', card.dataset.searchTo || '');
    p.set('depart', card.dataset.searchDepart || '');
    p.set('trip', (card.dataset.trip || '').trim() || 'one-way');
    var ret = (card.dataset.searchReturn || '').trim();
    if (ret) p.set('return', ret);
    var cab = (card.dataset.cabin || '').trim();
    if (cab) p.set('cabinClass', cab);
    var ad = (card.dataset.adults || '').trim();
    if (ad) p.set('adults', ad);
    var ch = (card.dataset.children || '').trim();
    if (ch) p.set('children', ch);
    var searchLeg = (card.dataset.searchLeg || '').trim();
    if (searchLeg) p.set('leg', searchLeg);
    if (searchLeg === 'inbound') {
      var obf = (card.dataset.obFrom || '').trim();
      var obt = (card.dataset.obTo || '').trim();
      var obd = (card.dataset.obDepart || '').trim();
      if (obf) p.set('obFrom', obf);
      if (obt) p.set('obTo', obt);
      if (obd) p.set('obDepart', obd);
      var obp = (card.dataset.outboundPrice || '').trim();
      if (obp) p.set('outboundPrice', obp);
      var obFlight = (card.dataset.obFlightId || '').trim();
      var obFare = (card.dataset.obFare || '').trim();
      if (obFlight) p.set('obFlight', obFlight);
      if (obFare) p.set('obFare', obFare);
    }
    p.set('fare', tier);
    var fid = (card.dataset.flightId || '').trim();
    if (fid) p.set('flight', fid);
    if (price) p.set('price', String(price));
    var depT = (card.dataset.cardDepartTime || '').trim();
    var arrT = (card.dataset.cardArrivalTime || '').trim();
    var dur = (card.dataset.cardDuration || '').trim();
    var fn = (card.dataset.cardFlights || '').trim();
    var arrP = (card.dataset.cardArrPlus || '').trim();
    var oc = (card.dataset.cardOriginCode || '').trim();
    var dc = (card.dataset.cardDestCode || '').trim();
    if (depT) p.set('segDep', depT);
    if (arrT) p.set('segArr', arrT);
    if (dur) p.set('segDur', dur);
    if (fn) p.set('segFlights', fn);
    if (arrP !== '') p.set('segArrPlus', arrP);
    if (oc) p.set('segOrig', oc);
    if (dc) p.set('segDest', dc);
    return p;
  }

  /** Step 2: review flight + fare before passenger details. */
  function buildReviewUrl(card, tier, price) {
    var p = buildBookingQueryParams(card, tier, price);
    p.set('highlight', '1');
    return '/book/review?' + p.toString();
  }

  /** Return leg: same UI as outbound results with swapped city pair and return date as depart. */
  function buildInboundSearchUrl(card, tier, price) {
    var p = new URLSearchParams();
    p.set('from', card.dataset.searchTo || '');
    p.set('to', card.dataset.searchFrom || '');
    p.set('depart', (card.dataset.searchReturn || '').trim());
    p.set('trip', 'return');
    p.set('return', '');
    p.set('leg', 'inbound');
    var cab = (card.dataset.cabin || '').trim();
    if (cab) p.set('cabinClass', cab);
    var ad = (card.dataset.adults || '').trim();
    if (ad) p.set('adults', ad);
    var ch = (card.dataset.children || '').trim();
    if (ch) p.set('children', ch);
    p.set('obFrom', card.dataset.searchFrom || '');
    p.set('obTo', card.dataset.searchTo || '');
    p.set('obDepart', card.dataset.searchDepart || '');
    p.set('fare', tier);
    var fid = (card.dataset.flightId || '').trim();
    if (fid) {
      p.set('flight', fid);
      p.set('obFlight', fid);
    }
    if (tier) p.set('obFare', tier);
    if (price) p.set('outboundPrice', String(price));
    return '/search-flights?' + p.toString();
  }

  function navigateAfterFareSelect(card, btn) {
    var tier = btn.getAttribute('data-fare-tier') || '';
    var price = btn.getAttribute('data-fare-price') || '';
    var trip = (card.dataset.trip || '').trim();
    var hasReturnDate = !!(card.dataset.searchReturn || '').trim();
    var url;
    var searchLeg = (card.dataset.searchLeg || '').trim();
    if (trip === 'return' && hasReturnDate && searchLeg !== 'inbound') {
      url = buildInboundSearchUrl(card, tier, price);
    } else {
      url = buildReviewUrl(card, tier, price);
    }
    window.location.assign(url);
  }

  function initFareSelect() {
    document.addEventListener('click', function (e) {
      var btn = e.target.closest('[data-fare-select]');
      if (!btn) return;
      var card = btn.closest('[data-flight-card]');
      if (!card) return;
      var grid = btn.closest('.fare-grid');
      if (!grid) return;
      e.preventDefault();

      if (prefersReducedMotion()) {
        navigateAfterFareSelect(card, btn);
        return;
      }

      var plan = btn.closest('.fare-plan');
      if (!plan || grid.classList.contains('fare-grid--select-busy')) return;
      grid.classList.add('fare-grid--select-busy');

      plan.classList.add('fare-plan--chosen');

      var plans = grid.querySelectorAll(':scope > .fare-plan');
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
    var detailsEls = document.querySelectorAll('.flight-card__details[data-route-details]');
    if (!detailsEls.length) return;

    detailsEls.forEach(function (details) {
      var summary = details.querySelector('summary.flight-card__details-summary');
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

          var body = details.querySelector('.flight-card__details-body');
          if (!body) {
            details.removeAttribute('open');
            return;
          }

          var finished = false;

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
    var roots = document.querySelectorAll('[data-bp-title-combo]');
    if (!roots.length) return;

    function closeAll() {
      roots.forEach(function (root) {
        var list = root.querySelector('.bp-wf-title-menu');
        var trigger = root.querySelector('.bp-wf-combo-trigger');
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
      var hidden = root.querySelector('input[type="hidden"]');
      var trigger = root.querySelector('.bp-wf-combo-trigger');
      var list = root.querySelector('.bp-wf-title-menu');
      if (!hidden || !trigger || !list) return;

      function syncLabel() {
        var v = (hidden.value || '').trim();
        trigger.textContent = v || 'Title';
      }

      trigger.addEventListener('click', function (e) {
        e.preventDefault();
        var opening = !list.classList.contains('is-open');
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
    var form = document.querySelector('[data-bp-passenger-form]');
    if (!form) return;

    var nameStripRe = /[^\p{L}\s'-]/gu;
    var nameFallbackRe = /[^A-Za-z\u00C0-\u024F\s'-]/g;

    function sanitizeNameValue(raw) {
      var s = String(raw || '');
      try {
        return s.replace(nameStripRe, '');
      } catch (e) {
        return s.replace(nameFallbackRe, '');
      }
    }

    function attachNameField(el) {
      function apply() {
        var next = sanitizeNameValue(el.value);
        if (el.value !== next) el.value = next;
      }
      el.addEventListener('input', apply);
      el.addEventListener('blur', apply);
      el.addEventListener('paste', function (e) {
        var text = (e.clipboardData || window.clipboardData || {}).getData('text') || '';
        if (!text) return;
        e.preventDefault();
        var merged =
          el.value.slice(0, el.selectionStart || 0) +
          text +
          el.value.slice(el.selectionEnd || 0);
        el.value = sanitizeNameValue(merged);
      });
      apply();
    }

    form.querySelectorAll('.bp-wf-input--pax-name').forEach(attachNameField);

    var dialVisible = document.getElementById('bp-contact-dial');
    var dialHidden = document.getElementById('bp-contact-dial-hidden');

    function syncDialHidden() {
      if (!dialVisible || !dialHidden) return;
      var digits = String(dialVisible.value || '').replace(/\D/g, '');
      dialVisible.value = digits;
      dialHidden.value = digits.length ? '+' + digits : '+';
    }

    if (dialVisible && dialHidden) {
      dialVisible.addEventListener('input', syncDialHidden);
      dialVisible.addEventListener('blur', syncDialHidden);
      dialVisible.addEventListener('paste', function (e) {
        var text = (e.clipboardData || window.clipboardData || {}).getData('text') || '';
        if (!text) return;
        e.preventDefault();
        var merged =
          dialVisible.value.slice(0, dialVisible.selectionStart || 0) +
          text +
          dialVisible.value.slice(dialVisible.selectionEnd || 0);
        dialVisible.value = merged.replace(/\D/g, '');
        syncDialHidden();
      });
      window.setTimeout(syncDialHidden, 0);
      window.setTimeout(syncDialHidden, 120);
    }

    var phoneEl = document.getElementById('bp-contact-phone');
    function syncPhoneDigits() {
      if (!phoneEl) return;
      var digits = String(phoneEl.value || '').replace(/\D/g, '');
      if (phoneEl.value !== digits) phoneEl.value = digits;
    }
    if (phoneEl) {
      phoneEl.addEventListener('input', syncPhoneDigits);
      phoneEl.addEventListener('blur', syncPhoneDigits);
      phoneEl.addEventListener('paste', function (e) {
        var text = (e.clipboardData || window.clipboardData || {}).getData('text') || '';
        if (!text) return;
        e.preventDefault();
        var merged =
          phoneEl.value.slice(0, phoneEl.selectionStart || 0) +
          text +
          phoneEl.value.slice(phoneEl.selectionEnd || 0);
        phoneEl.value = merged.replace(/\D/g, '');
      });
    }

    var emailEl = document.getElementById('bp-contact-email');

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

      var errDial = document.getElementById('bp-err-contact-dial');
      var errPhone = document.getElementById('bp-err-contact-phone');
      var errEmail = document.getElementById('bp-err-contact-email');

      var checks = [];

      form.querySelectorAll(':scope > fieldset').forEach(function (fs) {
        var hidden = fs.querySelector('input[type="hidden"][name$="_title"]');
        if (!hidden || !hidden.name) return;
        var m = hidden.name.match(/^pax_(\d+)_title$/);
        if (!m) return;
        var slot = m[1];
        var trigger = document.getElementById('bp-title-trigger-' + slot);
        var given = document.getElementById('bp-given-' + slot);
        var family = document.getElementById('bp-family-' + slot);
        var errTitle = document.getElementById('bp-err-pax-' + slot + '-title');
        var errGiven = document.getElementById('bp-err-pax-' + slot + '-given');
        var errFamily = document.getElementById('bp-err-pax-' + slot + '-family');

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

      for (var i = 0; i < checks.length; i++) {
        var c = checks[i];
        if (!c.test()) {
          showPassengerInlineError(c.errEl, c.msg, c.focusEl);
          return { ok: false, focusEl: c.focusEl };
        }
      }
      return { ok: true, focusEl: null };
    }

    form.addEventListener('submit', function (e) {
      var r = validatePassengerFormForSubmit();
      if (!r.ok) e.preventDefault();
    });

    var continueLink = form.querySelector('[data-bp-passenger-continue]');
    if (continueLink) {
      continueLink.addEventListener('click', function (e) {
        var r = validatePassengerFormForSubmit();
        if (!r.ok) e.preventDefault();
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

  window.addEventListener('pageshow', function (ev) {
    resetFareSelectionUi();
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
