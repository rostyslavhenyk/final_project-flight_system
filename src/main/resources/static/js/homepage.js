/**
 * Homepage behaviour (everything runs in the browser after HTML/CSS load).
 *
 * Contents (see each function’s block comment below):
 * - Airport autocomplete (“Leaving from” / “Going to”) + no-match message + forced select-all window
 * - Trip type + cabin class custom dropdowns
 * - Cabin / passengers modal
 * - Flatpickr date pickers (depart / return)
 * - Form validation before submit
 * - Latest offers: refetch from API, carousel scroll, image hover, lightbox
 *
 * The outer wrapper is an IIFE (Immediately Invoked Function Expression): (function() { ... })();
 * That creates a private scope so names like `input` or `list` do not become global `window` variables
 * and cannot clash with other scripts. `'use strict'` turns on stricter JavaScript rules (e.g. must declare variables).
 */
(function() {
  'use strict';

  /** Milliseconds after focus during which we keep re-selecting all text (user can still drag; selection snaps back). */
  var FORCE_SELECT_MS = 800;

  /**
   * Wires one airport text field (`#from` or `#to`) to its dropdown list (`#from-list` / `#to-list`).
   * HTML already contains every airport as `<div role="option" data-value="...">` from the server — there is no second request.
   *
   * Prefix match: an option is shown if the airport string (lowercase) STARTS WITH what the user typed (indexOf(q) === 0).
   * So "h" matches "Hong Kong (HKG)"; "hi" matches nothing → we show the empty-state paragraph.
   *
   * `document.getElementById(id)` returns the first element with that id, or null if missing.
   * Early `return` avoids errors if the template changed and an id is wrong.
   */
  function initAutocomplete(inputId, listId) {
    var input = document.getElementById(inputId);
    var list = document.getElementById(listId);
    if (!input || !list) return;

    /* Snapshot of all real options once; the empty-state `<p>` is NOT role="option" so it is not in this list. */
    var options = list.querySelectorAll('[role="option"]');

    /* Timestamp (ms since page load) until which we force “select all” on every tick; see startForceSelectAllWindow. */
    var forceSelectUntil = 0;
    /* ID returned by setInterval so we can clearInterval on blur. */
    var forceSelectIntervalId = null;

    function clearForceSelectAllWindow() {
      forceSelectUntil = 0;
      if (forceSelectIntervalId !== null) {
        clearInterval(forceSelectIntervalId);
        forceSelectIntervalId = null;
      }
    }

    /**
     * For FORCE_SELECT_MS after focus, we repeatedly call selectAll() on a short interval.
     * That way if the user slightly drags and the browser moves the caret, the next tick selects the whole value again.
     * After the window ends, normal caret behaviour returns (user can place the cursor for editing).
     */
    function startForceSelectAllWindow() {
      clearForceSelectAllWindow();
      forceSelectUntil = Date.now() + FORCE_SELECT_MS;
      forceSelectIntervalId = setInterval(function() {
        if (Date.now() < forceSelectUntil) {
          selectAll();
        } else {
          clearForceSelectAllWindow();
        }
      }, 40);
    }

    /**
     * Reads the input value, filters options, sorts matches, toggles the “no match” line, opens the dropdown.
     * `classList.add('is-open')` adds a CSS class; homepage.css uses `.autocomplete-dropdown.is-open { display: block }`.
     */
    function filter() {
      var q = (input.value || '').trim().toLowerCase();
      var matched = [];
      var emptyEl = list.querySelector('.autocomplete-dropdown__empty');
      options.forEach(function(el) {
        var value = (el.getAttribute('data-value') || '').trim();
        var valueLower = value.toLowerCase();
        var match = !q || valueLower.indexOf(q) === 0;
        el.style.display = match ? 'block' : 'none';
        if (match) matched.push(el);
      });
      matched
        .sort(function(a, b) {
          var av = (a.getAttribute('data-value') || '').toLowerCase();
          var bv = (b.getAttribute('data-value') || '').toLowerCase();
          if (av < bv) return -1;
          if (av > bv) return 1;
          return 0;
        })
        .forEach(function(el) { list.appendChild(el); });
      if (emptyEl) {
        list.appendChild(emptyEl);
        var showNoMatch = q.length > 0 && matched.length === 0;
        /* `hidden` is a boolean HTML attribute: hidden=false removes it so the message is visible. */
        emptyEl.hidden = !showNoMatch;
      }
      list.classList.add('is-open');
      input.setAttribute('aria-expanded', 'true');
    }

    function hide() {
      list.classList.remove('is-open');
      input.setAttribute('aria-expanded', 'false');
    }

    function choose(value) {
      input.value = value;
      hide();
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    /** Selects characters 0..length in the text control (or falls back to input.select()). */
    function selectAll() {
      var len = (input.value || '').length;
      try {
        input.setSelectionRange(0, len);
      } catch (e) {
        try {
          input.select();
        } catch (e2) {}
      }
    }

    function selectAllAfterPaint() {
      selectAll();
    }

    input.addEventListener('focus', function() {
      startForceSelectAllWindow();
      filter();
      if (typeof window.requestAnimationFrame === 'function') {
        window.requestAnimationFrame(function() {
          window.requestAnimationFrame(selectAllAfterPaint);
        });
      } else {
        setTimeout(selectAllAfterPaint, 0);
      }
    });

    /**
     * During the force window, preventDefault() on mouseup stops the browser from placing the caret after a click,
     * which would cancel a full selection. After the window, we allow default so the user can position the caret.
     * preventDefault() cancels the browser’s default action for that event; it does not stop propagation to other handlers.
     */
    input.addEventListener('mouseup', function(e) {
      if (Date.now() < forceSelectUntil) {
        e.preventDefault();
      }
    });
    input.addEventListener('input', filter);
    input.addEventListener('blur', function() {
      clearForceSelectAllWindow();
      setTimeout(hide, 150);
    });

    /**
     * mousedown (not click) on an option: default mousedown blurs the input before click fires; preventDefault keeps focus
     * so the option handler can run and set the value reliably.
     */
    list.querySelectorAll('[role="option"]').forEach(function(el) {
      el.addEventListener('mousedown', function(e) {
        e.preventDefault();
        choose(el.getAttribute('data-value'));
      });
    });
  }

  /** Maps internal trip values (form / server) to the label shown on the button. */
  var TRIP_LABELS = { 'one-way': 'One way', 'return': 'Return' };

  /**
   * Trip type is not a native `<select>`: a hidden input `#trip` holds `one-way` or `return`, and `#trip-trigger` toggles `#trip-list`.
   * `classList.contains('is-open')` returns true if that class is present on the element (boolean).
   * `element.contains(otherNode)` returns true if otherNode is inside element (used to detect “click outside” to close).
   */
  function initTripCombobox() {
    var tripInput = document.getElementById('trip');
    var trigger = document.getElementById('trip-trigger');
    var list = document.getElementById('trip-list');
    if (!tripInput || !trigger || !list) return;

    function hide() {
      list.classList.remove('is-open');
      trigger.setAttribute('aria-expanded', 'false');
    }

    function show() {
      list.classList.add('is-open');
      trigger.setAttribute('aria-expanded', 'true');
    }

    function setTrip(value) {
      tripInput.value = value;
      trigger.textContent = TRIP_LABELS[value] || value;
      hide();
      tripInput.dispatchEvent(new Event('change', { bubbles: true }));
    }

    trigger.addEventListener('click', function(e) {
      e.preventDefault();
      if (list.classList.contains('is-open')) hide();
      else show();
    });

    list.querySelectorAll('.trip-option').forEach(function(btn) {
      btn.addEventListener('click', function(e) {
        e.preventDefault();
        setTrip(btn.getAttribute('data-value'));
      });
    });

    document.addEventListener('click', function(e) {
      if (!trigger.contains(e.target) && !list.contains(e.target)) hide();
    });

    var initial = tripInput.value || 'one-way';
    trigger.textContent = TRIP_LABELS[initial] || initial;
  }

  var CABIN_CLASS_LABELS = {
    'economy': 'Economy',
    'business': 'Business',
    'first': 'First Class'
  };

  /** Builds the single-line summary on the cabin trigger button, e.g. "Economy, 1 adult". */
  function cabinSummaryText(cabinVal, adults, children) {
    var cls = CABIN_CLASS_LABELS[cabinVal] || cabinVal;
    var parts = [];
    parts.push(adults + (adults === 1 ? ' adult' : ' adults'));
    if (children > 0) {
      parts.push(children + (children === 1 ? ' child' : ' children'));
    }
    return cls + ', ' + parts.join(', ');
  }

  /**
   * Cabin + passengers live in a modal (`#cabin-modal` with `[hidden]` until opened).
   * `stopPropagation()` on the class dropdown click stops the event bubbling to `document`, which would otherwise
   * think the user clicked “outside” the cabin list and close it immediately.
   * Stepper buttons adjust `adults` / `children` variables and update the visible numbers; “Done” copies into hidden inputs.
   */
  function initCabinModal() {
    var trigger = document.getElementById('cabin-trigger');
    var modal = document.getElementById('cabin-modal');
    var done = document.getElementById('cabin-done');
    var cabinModalClassHidden = document.getElementById('cabin-modal-class');
    var cabinClassTrigger = document.getElementById('cabin-class-trigger');
    var cabinClassList = document.getElementById('cabin-class-list');
    var cabinHidden = document.getElementById('cabin-class-hidden');
    var adultsHidden = document.getElementById('adults-hidden');
    var childrenHidden = document.getElementById('children-hidden');
    var adultsDisplay = document.getElementById('adults-display');
    var childrenDisplay = document.getElementById('children-display');
    if (
      !trigger ||
      !modal ||
      !done ||
      !cabinModalClassHidden ||
      !cabinClassTrigger ||
      !cabinClassList ||
      !cabinHidden ||
      !adultsHidden ||
      !childrenHidden
    ) {
      return;
    }

    function hideCabinClassList() {
      cabinClassList.classList.remove('is-open');
      cabinClassTrigger.setAttribute('aria-expanded', 'false');
    }

    function showCabinClassList() {
      cabinClassList.classList.add('is-open');
      cabinClassTrigger.setAttribute('aria-expanded', 'true');
    }

    function setModalCabin(value) {
      cabinModalClassHidden.value = value;
      cabinClassTrigger.textContent = CABIN_CLASS_LABELS[value] || value;
      hideCabinClassList();
    }

    cabinClassTrigger.addEventListener('click', function(e) {
      e.preventDefault();
      e.stopPropagation();
      if (cabinClassList.classList.contains('is-open')) hideCabinClassList();
      else showCabinClassList();
    });

    cabinClassList.querySelectorAll('.trip-option').forEach(function(btn) {
      btn.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        setModalCabin(btn.getAttribute('data-value'));
      });
    });

    document.addEventListener('click', function(e) {
      if (!cabinClassTrigger.contains(e.target) && !cabinClassList.contains(e.target)) {
        hideCabinClassList();
      }
    });

    var adults = parseInt(adultsHidden.value, 10) || 1;
    var children = parseInt(childrenHidden.value, 10) || 0;

    function syncDisplays() {
      adultsDisplay.textContent = String(adults);
      childrenDisplay.textContent = String(children);
    }

    function maxChildrenAllowed() {
      return Math.max(0, 9 - adults);
    }

    function maxAdultsAllowed() {
      return Math.max(1, 9 - children);
    }

    function wireStepper(container, kind) {
      if (!container) return;
      container.querySelectorAll('.stepper__btn').forEach(function(btn) {
        btn.addEventListener('click', function(e) {
          e.preventDefault();
          var dir = parseInt(btn.getAttribute('data-dir'), 10);
          if (kind === 'adults') {
            adults = Math.min(maxAdultsAllowed(), Math.max(1, adults + dir));
          } else {
            children = Math.min(maxChildrenAllowed(), Math.max(0, children + dir));
          }
          syncDisplays();
        });
      });
    }

    wireStepper(document.querySelector('[data-stepper="adults"]'), 'adults');
    wireStepper(document.querySelector('[data-stepper="children"]'), 'children');

    function openModal() {
      adults = parseInt(adultsHidden.value, 10) || 1;
      children = parseInt(childrenHidden.value, 10) || 0;
      var cv = cabinHidden.value || 'economy';
      cabinModalClassHidden.value = cv;
      cabinClassTrigger.textContent = CABIN_CLASS_LABELS[cv] || cv;
      hideCabinClassList();
      syncDisplays();
      modal.removeAttribute('hidden');
      trigger.setAttribute('aria-expanded', 'true');
    }

    function closeModal() {
      hideCabinClassList();
      modal.setAttribute('hidden', '');
      trigger.setAttribute('aria-expanded', 'false');
    }

    trigger.addEventListener('click', function(e) {
      e.preventDefault();
      if (modal.hasAttribute('hidden')) openModal();
      else closeModal();
    });

    modal.querySelectorAll('[data-close-cabin]').forEach(function(el) {
      el.addEventListener('click', function(e) {
        e.preventDefault();
        closeModal();
      });
    });

    done.addEventListener('click', function(e) {
      e.preventDefault();
      cabinHidden.value = cabinModalClassHidden.value;
      adultsHidden.value = String(adults);
      childrenHidden.value = String(children);
      trigger.textContent = cabinSummaryText(cabinModalClassHidden.value, adults, children);
      closeModal();
    });

    trigger.textContent = cabinSummaryText(cabinHidden.value || 'economy', adults, children);
    syncDisplays();
  }

  /**
   * Date fields use Flatpickr (loaded from CDN in index.peb before this file). `flatpickr(button, options)` attaches a calendar popup.
   * We store ISO dates `YYYY-MM-DD` in hidden inputs `#depart` / `#return` for the GET form; the visible labels are formatted in en-GB.
   * `fpReturn.set('minDate', date)` ensures return is not before departure. `setReturnEnabled(false)` disables return for one-way trips.
   */
  function initDatePickers() {
    var trip = document.getElementById('trip');
    var departTrigger = document.getElementById('depart-trigger');
    var returnTrigger = document.getElementById('return-trigger');
    var departInput = document.getElementById('depart');
    var returnInput = document.getElementById('return');
    if (!departTrigger || !returnTrigger || !departInput || !returnInput) return;

    function formatDate(d) {
      return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
    }

    function toLocalIsoDate(d) {
      var y = d.getFullYear();
      var m = String(d.getMonth() + 1).padStart(2, '0');
      var day = String(d.getDate()).padStart(2, '0');
      return y + '-' + m + '-' + day;
    }

    function setReturnEnabled(enabled) {
      returnTrigger.disabled = !enabled;
      if (!enabled) {
        returnInput.value = '';
        returnTrigger.textContent = 'One way';
      } else {
        if (!returnInput.value) returnTrigger.textContent = 'Select date';
      }
    }

    var fpDepart = flatpickr(departTrigger, {
      mode: 'single',
      dateFormat: 'Y-m-d',
      minDate: 'today',
      onChange: function(selected) {
        if (selected.length) {
          departInput.value = toLocalIsoDate(selected[0]);
          departTrigger.textContent = formatDate(selected[0]);
          if (fpReturn) fpReturn.set('minDate', selected[0]);
        }
      }
    });

    var fpReturn = flatpickr(returnTrigger, {
      mode: 'single',
      dateFormat: 'Y-m-d',
      minDate: departInput.value || 'today',
      onChange: function(selected) {
        if (selected.length) {
          returnInput.value = toLocalIsoDate(selected[0]);
          returnTrigger.textContent = formatDate(selected[0]);
        }
      }
    });

    departTrigger.addEventListener('click', function(e) { e.preventDefault(); });
    returnTrigger.addEventListener('click', function(e) {
      e.preventDefault();
      if (returnTrigger.disabled) return;
    });

    trip.addEventListener('change', function() {
      if (trip.value === 'one-way') {
        setReturnEnabled(false);
      } else {
        setReturnEnabled(true);
        var d = departInput.value;
        if (d) fpReturn.set('minDate', d);
      }
    });

    departTrigger.textContent = 'Select date';
    setReturnEnabled(trip && trip.value === 'return');
  }

  /**
   * Intercepts form submit. `e.preventDefault()` stops the browser from navigating away if validation fails.
   * `fail()` shows `#flight-search-error` and focuses it for screen readers. Airport strings must exactly match a `data-value` from the list.
   */
  function initFlightSearchValidation() {
    var form = document.querySelector('.flight-search-form');
    var err = document.getElementById('flight-search-error');
    if (!form || !err) return;

    form.addEventListener('submit', function(e) {
      var fromEl = document.getElementById('from');
      var toEl = document.getElementById('to');
      var trip = document.getElementById('trip');
      var departEl = document.getElementById('depart');
      var returnEl = document.getElementById('return');

      var from = (fromEl && fromEl.value || '').trim();
      var to = (toEl && toEl.value || '').trim();
      var depart = (departEl && departEl.value || '').trim();
      var ret = (returnEl && returnEl.value || '').trim();

      function fail(msg) {
        e.preventDefault();
        err.textContent = msg;
        err.removeAttribute('hidden');
        err.focus();
      }

      err.setAttribute('hidden', '');
      err.textContent = '';

      if (!from) {
        fail('Please choose where you are leaving from.');
        return;
      }
      if (!to) {
        fail('Please choose where you are going to.');
        return;
      }
      if (!depart) {
        fail('Please select a departing date.');
        return;
      }
      if (trip && trip.value === 'return') {
        if (!ret) {
          fail('Please select a return date for a return trip.');
          return;
        }
        if (ret < depart) {
          fail('Return date cannot be before the departing date. Please pick a return date on or after your departure.');
          return;
        }
      }

      var validFrom = airportValuesFromList('from-list');
      var validTo = airportValuesFromList('to-list');
      if (Object.keys(validFrom).length && !validFrom[from]) {
        fail('Please choose a valid airport from the suggestions for “Leaving from”.');
        return;
      }
      if (Object.keys(validTo).length && !validTo[to]) {
        fail('Please choose a valid airport from the suggestions for “Going to”.');
        return;
      }
      if (from === to) {
        fail('Leaving from and going to must be different airports.');
        return;
      }
    });
  }

  /** Builds a lookup object { "Manchester (MAN)": true, ... } from `[role="option"]` nodes for validation. */
  function airportValuesFromList(listId) {
    var list = document.getElementById(listId);
    if (!list) return {};
    var set = {};
    list.querySelectorAll('[role="option"]').forEach(function(el) {
      var v = el.getAttribute('data-value');
      if (v) set[v.trim()] = true;
    });
    return set;
  }

  /** Separator that cannot appear in URLs; used in `data-images` and split in the lightbox. */
  var IMG_JOIN = '|||GLIDE|||';

  function parseImagesFromStack(stack) {
    var raw = stack.getAttribute('data-images');
    if (!raw) return [];
    return raw.split(IMG_JOIN).filter(Boolean);
  }

  /**
   * Each offer card has stacked `.offer-card-slide` divs (background images). CSS hides non-active slides with opacity.
   * On mouseenter we `setInterval` every 3000 ms and call `setActive` to advance `idx`; `classList.toggle('is-active', condition)` sets the class only when true.
   * `% slides.length` wraps the index (after last image, back to first). `matchMedia('(prefers-reduced-motion: reduce)')` skips animation if the user asked for reduced motion.
   * `dataset.hoverWired = '1'` is a guard so we do not attach duplicate listeners if `renderOffersFromJson` runs again.
   */
  function wireOfferCardImageStacks(root) {
    if (!root) return;
    var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    root.querySelectorAll('.offer-card-image-stack').forEach(function(stack) {
      if (stack.dataset.hoverWired === '1') return;
      stack.dataset.hoverWired = '1';
      var slides = stack.querySelectorAll('.offer-card-slide');
      if (slides.length < 2 || reduceMotion) return;
      var timer = null;
      var idx = 0;
      function setActive(i) {
        idx = i % slides.length;
        slides.forEach(function(s, j) {
          s.classList.toggle('is-active', j === idx);
        });
      }
      setActive(0);
      stack.addEventListener('mouseenter', function() {
        clearInterval(timer);
        setActive(0);
        /* Every 3s move to next slide: setActive(idx+1) bumps idx inside setActive via modulo. */
        timer = setInterval(function() {
          setActive(idx + 1);
        }, 3000);
      });
      stack.addEventListener('mouseleave', function() {
        clearInterval(timer);
        timer = null;
        idx = 0;
        setActive(0);
      });
    });
  }

  /** Replaces `#offers-cards` inner HTML from `/api/latest-offers` JSON; then wires hover + lightbox on new nodes. */
  function renderOffersFromJson(data) {
    var label = document.getElementById('offers-origin-label');
    if (label) label.textContent = data.originLabel || '';
    var section = document.querySelector('.latest-offers');
    if (section && data.originCode) section.setAttribute('data-default-origin', data.originCode);
    var ul = document.getElementById('offers-cards');
    if (!ul) return;
    ul.innerHTML = '';
    var cards = data.cards || [];
    cards.forEach(function(c) {
      /* Build the same structure as Pebble SSR: li > image stack + h3 + meta + book button. */
      var li = document.createElement('li');
      li.className = 'offer-card';
      var urls = c.imageUrls && c.imageUrls.length ? c.imageUrls : (c.imageUrl ? [c.imageUrl] : []);
      var stack = document.createElement('div');
      stack.className = 'offer-card-image-stack';
      stack.setAttribute('role', 'button');
      stack.setAttribute('tabindex', '0');
      stack.setAttribute(
        'aria-label',
        (c.destinationName || 'Destination') + ' — hover to see more photos, click to open full-size gallery'
      );
      stack.setAttribute('data-dest-name', c.destinationName || '');
      stack.setAttribute('data-images', urls.join(IMG_JOIN));
      urls.forEach(function(u, idx) {
        var slide = document.createElement('div');
        slide.className = 'offer-card-slide' + (idx === 0 ? ' is-active' : '');
        slide.style.backgroundImage = 'url("' + String(u).replace(/"/g, '') + '")';
        slide.style.backgroundSize = 'cover';
        slide.style.backgroundPosition = 'center';
        slide.setAttribute('aria-hidden', 'true');
        stack.appendChild(slide);
      });
      li.appendChild(stack);
      var h3 = document.createElement('h3');
      h3.className = 'offer-card-dest';
      h3.textContent = c.destinationName || '';
      li.appendChild(h3);
      var p = document.createElement('p');
      p.className = 'offer-card-meta';
      p.textContent = 'Economy · from GBP ' + (c.priceGbp != null ? c.priceGbp : '');
      li.appendChild(p);
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'offer-card-cta';
      if (c.bookAirport) btn.setAttribute('data-book-airport', c.bookAirport);
      btn.textContent = 'Book now →';
      li.appendChild(btn);
      ul.appendChild(li);
    });
    if (!cards.length) {
      var li = document.createElement('li');
      li.className = 'offer-card';
      var p = document.createElement('p');
      p.className = 'offer-card-meta';
      p.textContent =
        'Fares are not available for this departure airport. Please choose another airport or contact reservations.';
      li.appendChild(p);
      ul.appendChild(li);
    }
    wireOfferCardImageStacks(ul);
  }

  /**
   * When “Leaving from” contains a full airport string ending in `(XXX)`, we fetch new offers for that IATA code.
   * `lastOfferFetchKey` avoids refetching the same origin. `encodeURIComponent` makes the URL safe. `fetch` returns a Promise; `.then` runs when JSON arrives.
   */
  function initOffersFromLeavingFrom() {
    var fromInput = document.getElementById('from');
    var section = document.querySelector('.latest-offers');
    if (!fromInput || !section) return;

    var debounceId;
    var lastOfferFetchKey =
      (section.getAttribute('data-ssr-origin') || 'MAN').trim() +
      '|' +
      (section.getAttribute('data-ssr-label') || 'Manchester').trim();

    function fetchOffers(originCode, originLabel) {
      var key = originCode + '|' + originLabel;
      if (key === lastOfferFetchKey) return;
      var q =
        '/api/latest-offers?origin=' +
        encodeURIComponent(originCode) +
        '&originLabel=' +
        encodeURIComponent(originLabel);
      fetch(q)
        .then(function(r) { return r.json(); })
        .then(function(data) {
          lastOfferFetchKey = key;
          renderOffersFromJson(data);
        })
        .catch(function() {});
    }

    function sync() {
      var v = (fromInput.value || '').trim();
      var fallbackCode = (section.getAttribute('data-ssr-origin') || 'MAN').trim();
      var fallbackLabel = (section.getAttribute('data-ssr-label') || 'Manchester').trim();

      if (!v) {
        fetchOffers(fallbackCode, fallbackLabel);
        return;
      }

      var m = v.match(/\(([A-Z]{3})\)\s*$/);
      if (!m) return;

      var code = m[1];
      var display = v.replace(/\s*\([A-Z]{3}\)\s*$/, '').trim() || v;
      fetchOffers(code, display);
    }

    fromInput.addEventListener('change', sync);
    fromInput.addEventListener('blur', function() {
      clearTimeout(debounceId);
      debounceId = setTimeout(sync, 200);
    });
    fromInput.addEventListener('input', function() {
      clearTimeout(debounceId);
      debounceId = setTimeout(function() {
        var v = (fromInput.value || '').trim();
        if (/\([A-Z]{3}\)\s*$/.test(v)) sync();
      }, 400);
    });

    if ((fromInput.value || '').trim()) sync();
  }

  /**
   * Delegated click on `#offers-cards`: if the click target is inside `.offer-card-cta`, `closest` finds that button.
   * We copy `data-book-airport` into `#to` and dispatch `change` so any other logic can react.
   */
  function initBookNowFromOffers() {
    var ul = document.getElementById('offers-cards');
    if (!ul) return;
    ul.addEventListener('click', function(e) {
      var btn = e.target.closest('.offer-card-cta');
      if (!btn) return;
      e.preventDefault();
      var v = btn.getAttribute('data-book-airport');
      if (!v) return;
      var toInput = document.getElementById('to');
      if (!toInput) return;
      toInput.value = v;
      toInput.dispatchEvent(new Event('change', { bubbles: true }));
      var err = document.getElementById('flight-search-error');
      if (err) {
        err.setAttribute('hidden', '');
        err.textContent = '';
      }
    });
  }

  /**
   * Full-screen gallery dialog `#offer-lightbox`. `state` holds the URL array and current index.
   * Clicks on `.offer-card-image-stack` open it; clicks on `.offer-card-cta` return early so booking is not mistaken for gallery.
   * `document.body.style.overflow = 'hidden'` stops background scrolling while open. Keydown Escape / arrows handled on `document`.
   */
  function initOfferLightbox() {
    var ul = document.getElementById('offers-cards');
    var lb = document.getElementById('offer-lightbox');
    if (!ul || !lb) return;
    var imgEl = document.getElementById('offer-lightbox-img');
    var titleEl = document.getElementById('offer-lightbox-title');
    var capEl = document.getElementById('offer-lightbox-caption');
    var ctrEl = document.getElementById('offer-lightbox-counter');
    var prevBtn = document.getElementById('offer-lightbox-prev');
    var nextBtn = document.getElementById('offer-lightbox-next');
    var closeBtn = lb.querySelector('.offer-lightbox__close');
    var state = { images: [], idx: 0, name: '' };

    function closeLb() {
      lb.setAttribute('hidden', '');
      document.body.style.overflow = '';
      if (imgEl) imgEl.removeAttribute('src');
      state.images = [];
    }

    function showLb(i) {
      if (!state.images.length || !imgEl || !titleEl || !capEl || !ctrEl) return;
      var n = state.images.length;
      state.idx = ((i % n) + n) % n;
      var u = state.images[state.idx];
      imgEl.src = u;
      imgEl.alt = state.name + ' — photo ' + (state.idx + 1);
      titleEl.textContent = state.name;
      capEl.textContent = state.name + ' · photo ' + (state.idx + 1) + ' of ' + n;
      ctrEl.textContent = state.idx + 1 + ' / ' + n;
    }

    function openLb(stack) {
      var images = parseImagesFromStack(stack);
      if (!images.length) return;
      state.images = images;
      state.name = stack.getAttribute('data-dest-name') || 'Destination';
      lb.removeAttribute('hidden');
      document.body.style.overflow = 'hidden';
      showLb(0);
      if (closeBtn) closeBtn.focus();
    }

    ul.addEventListener('click', function(e) {
      if (e.target.closest('.offer-card-cta')) return;
      var stack = e.target.closest('.offer-card-image-stack');
      if (!stack) return;
      e.preventDefault();
      openLb(stack);
    });

    lb.querySelectorAll('[data-offer-lightbox-close]').forEach(function(el) {
      el.addEventListener('click', closeLb);
    });
    if (prevBtn) prevBtn.addEventListener('click', function() { showLb(state.idx - 1); });
    if (nextBtn) nextBtn.addEventListener('click', function() { showLb(state.idx + 1); });
    document.addEventListener('keydown', function(e) {
      if (lb.hasAttribute('hidden')) return;
      if (e.key === 'Escape') closeLb();
      if (e.key === 'ArrowLeft') showLb(state.idx - 1);
      if (e.key === 'ArrowRight') showLb(state.idx + 1);
    });

    ul.addEventListener('keydown', function(e) {
      var stack = e.target.closest('.offer-card-image-stack');
      if (!stack || (e.key !== 'Enter' && e.key !== ' ')) return;
      e.preventDefault();
      openLb(stack);
    });
  }

  /**
   * Horizontal scroll area `#offers-scroll`. Arrow buttons call `jumpByCards`: find the card index nearest current `scrollLeft`,
   * then `scrollTo` the card at index ±4 so each click moves one “page” of four cards. `offsetLeft` is relative to the offset parent.
   * Wheel listener uses `preventDefault` only in some cases (needs `{ passive: false }` so preventDefault is allowed).
   */
  function initOffersCarousel() {
    var scrollEl = document.getElementById('offers-scroll');
    var prev = document.getElementById('offers-prev');
    var next = document.getElementById('offers-next');
    if (!scrollEl) return;

    scrollEl.addEventListener('wheel', function(e) {
      if (scrollEl.scrollWidth <= scrollEl.clientWidth) return;
      var dx = e.deltaX;
      var dy = e.deltaY;
      if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 0.5) {
        e.preventDefault();
        return;
      }
      if (e.shiftKey && Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 0.5) {
        e.preventDefault();
      }
    }, { passive: false });

    function offerCards() {
      var ul = scrollEl.querySelector('.offer-cards');
      if (!ul) return [];
      return ul.querySelectorAll(':scope > .offer-card');
    }

    function nearestCardIndex(cards) {
      var left = scrollEl.scrollLeft;
      var best = 0;
      var bestDist = Number.POSITIVE_INFINITY;
      cards.forEach(function(card, idx) {
        var d = Math.abs(card.offsetLeft - left);
        if (d < bestDist) {
          bestDist = d;
          best = idx;
        }
      });
      return best;
    }

    function jumpByCards(direction) {
      var cards = offerCards();
      if (!cards.length) return;
      if (cards.length <= 4) {
        scrollEl.scrollBy({ left: direction * (scrollEl.clientWidth || 280), behavior: 'smooth' });
        return;
      }
      var current = nearestCardIndex(cards);
      var target = Math.max(0, Math.min(cards.length - 1, current + (direction * 4)));
      scrollEl.scrollTo({ left: cards[target].offsetLeft, behavior: 'smooth' });
    }
    if (prev) {
      prev.addEventListener('click', function() {
        jumpByCards(-1);
      });
    }
    if (next) {
      next.addEventListener('click', function() {
        jumpByCards(1);
      });
    }
  }

  /** Runs all initialisers once the DOM is ready. `flatpickr` must exist (script tag above); if not, dates stay uninitialised. */
  function onReady() {
    initAutocomplete('from', 'from-list');
    initAutocomplete('to', 'to-list');
    initTripCombobox();
    initCabinModal();
    initOffersCarousel();
    initOffersFromLeavingFrom();
    initBookNowFromOffers();
    wireOfferCardImageStacks(document.getElementById('offers-cards'));
    initOfferLightbox();
    if (typeof flatpickr !== 'undefined') {
      initDatePickers();
    }
    initFlightSearchValidation();
  }

  /* If the script runs after parse (e.g. defer), DOM may already be ready — call onReady immediately; else wait for DOMContentLoaded. */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', onReady);
  } else {
    onReady();
  }
})();
