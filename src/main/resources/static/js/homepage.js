/* global attachForceSelectAll */
(function() {
  'use strict';
  let FORCE_SELECT_MS = 800;
  /** Airport autocomplete with same-airport exclusion. */
  function initAutocomplete(inputId, listId, otherInputId) {
    let input = document.getElementById(inputId);
    let list = document.getElementById(listId);
    let otherInput = otherInputId ? document.getElementById(otherInputId) : null;
    if (!input || !list) return;
    let options = list.querySelectorAll('[role="option"]');
    function filter() {
      let queryText = (input.value || '').trim().toLowerCase();
      let unavailableValue = (otherInput && otherInput.value || '').trim().toLowerCase();
      let matched = [];
      let emptyEl = list.querySelector('.autocomplete-dropdown__empty');
      options.forEach(function(el) {
        let value = (el.getAttribute('data-value') || '').trim();
        let valueLower = value.toLowerCase();
        let sameAsOtherAirport = !!unavailableValue && valueLower === unavailableValue;
        let match = !sameAsOtherAirport && (!queryText || valueLower.indexOf(queryText) === 0);
        el.style.display = match ? 'block' : 'none';
        el.setAttribute('aria-disabled', sameAsOtherAirport ? 'true' : 'false');
        if (match) matched.push(el);
      });
      matched
        .sort(function(a, b) {
          let av = (a.getAttribute('data-value') || '').toLowerCase();
          let bv = (b.getAttribute('data-value') || '').toLowerCase();
          if (av < bv) return -1;
          if (av > bv) return 1;
          return 0;
        })
        .forEach(function(el) { list.appendChild(el); });
      if (emptyEl) {
        list.appendChild(emptyEl);
        let showNoMatch = queryText.length > 0 && matched.length === 0;
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

    function clearIfSameAsOther() {
      if (!otherInput) return;
      let ownValue = (input.value || '').trim();
      let otherValue = (otherInput.value || '').trim();
      if (!ownValue || !otherValue || ownValue !== otherValue) return;
      input.value = '';
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }
    input.addEventListener('focus', filter);
    if (typeof window.attachForceSelectAll === 'function') {
      window.attachForceSelectAll(input, { forceSelectMs: FORCE_SELECT_MS });
    }
    input.addEventListener('input', filter);
    input.addEventListener('blur', function() {
      setTimeout(hide, 150);
    });
    list.querySelectorAll('[role="option"]').forEach(function(el) {
      el.addEventListener('mousedown', function(e) {
        e.preventDefault();
        if (el.getAttribute('aria-disabled') === 'true') return;
        choose(el.getAttribute('data-value'));
      });
    });

    input.addEventListener('change', clearIfSameAsOther);
    if (otherInput) otherInput.addEventListener('change', clearIfSameAsOther);
  }
  let TRIP_LABELS = { 'one-way': 'One way', 'return': 'Return' };
  /** Custom trip selector. */
  function initTripCombobox() {
    let tripInput = document.getElementById('trip');
    let trigger = document.getElementById('trip-trigger');
    let list = document.getElementById('trip-list');
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

    let initial = tripInput.value || 'one-way';
    trigger.textContent = TRIP_LABELS[initial] || initial;
  }

  let CABIN_CLASS_LABELS = {
    'economy': 'Economy',
    'business': 'Business'
  };
  function cabinSummaryText(cabinVal, adults, children) {
    let cls = CABIN_CLASS_LABELS[cabinVal] || cabinVal;
    let parts = [];
    parts.push(adults + (adults === 1 ? ' adult' : ' adults'));
    if (children > 0) {
      parts.push(children + (children === 1 ? ' child' : ' children'));
    }
    return cls + ', ' + parts.join(', ');
  }
  /** Cabin and passenger modal. */
  function initCabinModal() {
    let trigger = document.getElementById('cabin-trigger');
    let modal = document.getElementById('cabin-modal');
    let done = document.getElementById('cabin-done');
    let cabinModalClassHidden = document.getElementById('cabin-modal-class');
    let cabinClassTrigger = document.getElementById('cabin-class-trigger');
    let cabinClassList = document.getElementById('cabin-class-list');
    let cabinHidden = document.getElementById('cabin-class-hidden');
    let adultsHidden = document.getElementById('adults-hidden');
    let childrenHidden = document.getElementById('children-hidden');
    let adultsDisplay = document.getElementById('adults-display');
    let childrenDisplay = document.getElementById('children-display');
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
        if (btn.disabled) return;
        setModalCabin(btn.getAttribute('data-value'));
      });
    });

    let fromEl = document.getElementById('from');
    let toEl = document.getElementById('to');

  /* Business is disabled on restricted regional routes. */
    function syncBusinessAvailability() {
      if (!fromEl || !toEl) return;
      let businessBtn = cabinClassList.querySelector('[data-value="business"]');
      if (!businessBtn) return;
      let q =
        'from=' +
        encodeURIComponent(fromEl.value || '') +
        '&to=' +
        encodeURIComponent(toEl.value || '');
      fetch('/api/homepage-cabin-constraints?' + q)
        .then(function(r) {
          return r.json();
        })
        .then(function(data) {
          let selectable = !data || data.businessSelectable !== false;
          if (selectable) {
            businessBtn.disabled = false;
            businessBtn.removeAttribute('aria-disabled');
            businessBtn.classList.remove('trip-option--disabled');
          } else {
            businessBtn.disabled = true;
            businessBtn.setAttribute('aria-disabled', 'true');
            businessBtn.classList.add('trip-option--disabled');
            if (
              (cabinHidden.value || '') === 'business' ||
              (cabinModalClassHidden && cabinModalClassHidden.value === 'business')
            ) {
              setModalCabin('economy');
              cabinHidden.value = 'economy';
              adults = parseInt(adultsHidden.value, 10) || 1;
              children = parseInt(childrenHidden.value, 10) || 0;
              trigger.textContent = cabinSummaryText('economy', adults, children);
            }
          }
        })
        .catch(function() {
          businessBtn.disabled = false;
          businessBtn.removeAttribute('aria-disabled');
          businessBtn.classList.remove('trip-option--disabled');
        });
    }

    if (fromEl && toEl) {
      fromEl.addEventListener('change', syncBusinessAvailability);
      toEl.addEventListener('change', syncBusinessAvailability);
    }

    document.addEventListener('click', function(e) {
      if (!cabinClassTrigger.contains(e.target) && !cabinClassList.contains(e.target)) {
        hideCabinClassList();
      }
    });

    let adults = parseInt(adultsHidden.value, 10) || 1;
    let children = parseInt(childrenHidden.value, 10) || 0;

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
          let dir = parseInt(btn.getAttribute('data-dir'), 10);
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
      let cv = cabinHidden.value || 'economy';
      cabinModalClassHidden.value = cv;
      cabinClassTrigger.textContent = CABIN_CLASS_LABELS[cv] || cv;
      hideCabinClassList();
      syncDisplays();
      syncBusinessAvailability();
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
    syncBusinessAvailability();
  }

  /** Flatpickr date controls with ISO hidden values. */
  function initDatePickers() {
    let flatpickrFactory = window['flatpickr'];
    if (typeof flatpickrFactory !== 'function') return;
    let trip = document.getElementById('trip');
    let departTrigger = document.getElementById('depart-trigger');
    let returnTrigger = document.getElementById('return-trigger');
    let departInput = document.getElementById('depart');
    let returnInput = document.getElementById('return');
    if (!trip || !departTrigger || !returnTrigger || !departInput || !returnInput) return;

    function formatDate(dateValue) {
      return dateValue.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
    }

    function toLocalIsoDate(dateValue) {
      let year = dateValue.getFullYear();
      let month = String(dateValue.getMonth() + 1).padStart(2, '0');
      let day = String(dateValue.getDate()).padStart(2, '0');
      return year + '-' + month + '-' + day;
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

    flatpickrFactory(departTrigger, {
      mode: 'single',
      dateFormat: 'Y-m-d',
      minDate: 'today',
      onChange: function(selected) {
        if (selected.length) {
          departInput.value = toLocalIsoDate(selected[0]);
          departInput.dispatchEvent(new Event('change', { bubbles: true }));
          departTrigger.textContent = formatDate(selected[0]);
          if (fpReturn) fpReturn.set('minDate', selected[0]);
        }
      }
    });

    let fpReturn = flatpickrFactory(returnTrigger, {
      mode: 'single',
      dateFormat: 'Y-m-d',
      minDate: departInput.value || 'today',
      onChange: function(selected) {
        if (selected.length) {
          returnInput.value = toLocalIsoDate(selected[0]);
          returnInput.dispatchEvent(new Event('change', { bubbles: true }));
          returnTrigger.textContent = formatDate(selected[0]);
        }
      }
    });

    departTrigger.addEventListener('click', function(e) { e.preventDefault(); });
    returnTrigger.addEventListener('click', function(e) {
      e.preventDefault();
    });

    trip.addEventListener('change', function() {
      if (trip.value === 'one-way') {
        setReturnEnabled(false);
      } else {
        setReturnEnabled(true);
        let departDate = departInput.value;
        if (departDate) fpReturn.set('minDate', departDate);
      }
    });

    departTrigger.textContent = 'Select date';
    setReturnEnabled(trip && trip.value === 'return');
  }

  /** Validates the homepage flight search before submit. */
  function initFlightSearchValidation() {
    let form = document.querySelector('.flight-search-form');
    let err = document.getElementById('flight-search-error');
    if (!form || !err) return;

    form.addEventListener('submit', function(e) {
      let fromEl = document.getElementById('from');
      let toEl = document.getElementById('to');
      let trip = document.getElementById('trip');
      let departEl = document.getElementById('depart');
      let returnEl = document.getElementById('return');

      let from = (fromEl && fromEl.value || '').trim();
      let to = (toEl && toEl.value || '').trim();
      let depart = (departEl && departEl.value || '').trim();
      let ret = (returnEl && returnEl.value || '').trim();

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

      let validFrom = airportValuesFromList('from-list');
      let validTo = airportValuesFromList('to-list');
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

  /** Builds the valid airport lookup from dropdown options. */
  function airportValuesFromList(listId) {
    let list = document.getElementById(listId);
    if (!list) return {};
    let set = {};
    list.querySelectorAll('[role="option"]').forEach(function(el) {
      let airportValue = el.getAttribute('data-value');
      if (airportValue) set[airportValue.trim()] = true;
    });
    return set;
  }

  /** Separator used in `data-images`. */
  let IMG_JOIN = '|||GLIDE|||';

  function parseImagesFromStack(stack) {
    let raw = stack.getAttribute('data-images');
    if (!raw) return [];
    return raw.split(IMG_JOIN).filter(Boolean);
  }

  /** Wires hover image stacks on offer cards. */
  function wireOfferCardImageStacks(root) {
    if (!root) return;
    let reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    root.querySelectorAll('.offer-card-image-stack').forEach(function(stack) {
      if (stack.dataset.hoverWired === '1') return;
      stack.dataset.hoverWired = '1';
      let slides = stack.querySelectorAll('.offer-card-slide');
      if (slides.length < 2 || reduceMotion) return;
      let timer = null;
      let idx = 0;
      function loadDeferredSlides() {
        slides.forEach(function(slide) {
          let bg = slide.getAttribute('data-bg');
          if (!bg) return;
          slide.style.backgroundImage = 'url("' + bg.replace(/"/g, '') + '")';
          slide.style.backgroundSize = 'cover';
          slide.style.backgroundPosition = 'center';
          slide.removeAttribute('data-bg');
        });
      }
      function setActive(nextIndex) {
        idx = nextIndex % slides.length;
        slides.forEach(function(s, j) {
          s.classList.toggle('is-active', j === idx);
        });
      }
      setActive(0);
      stack.addEventListener('mouseenter', function() {
        clearInterval(timer);
        loadDeferredSlides();
        setActive(0);
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

  function loadDeferredOfferImages(root) {
    if (!root) return;
    root.querySelectorAll('.offer-card-slide[data-bg]').forEach(function(slide) {
      let bg = slide.getAttribute('data-bg');
      if (!bg) return;
      slide.style.backgroundImage = 'url("' + bg.replace(/"/g, '') + '")';
      slide.style.backgroundSize = 'cover';
      slide.style.backgroundPosition = 'center';
      slide.removeAttribute('data-bg');
    });
  }

  function scheduleOfferImagePreload(root) {
    if (!root) return;
    let load = function() {
      loadDeferredOfferImages(root);
    };
    if ('requestIdleCallback' in window) {
      window.requestIdleCallback(load, { timeout: 1500 });
    } else {
      window.setTimeout(load, 700);
    }
  }
  /** Renders latest offers returned by the API. */
  function renderOffersFromJson(data) {
    let label = document.getElementById('offers-origin-label');
    if (label) label.textContent = data.originLabel || '';
    let section = document.querySelector('.latest-offers');
    if (section && data.originCode) section.setAttribute('data-default-origin', data.originCode);
    let ul = document.getElementById('offers-cards');
    if (!ul) return;
    ul.innerHTML = '';
    const offerDataCards = data.cards || [];
    offerDataCards.forEach(function(c) {
      let li = document.createElement('li');
      li.className = 'offer-card';
      let urls = c.imageUrls && c.imageUrls.length ? c.imageUrls : (c.imageUrl ? [c.imageUrl] : []);
      let stack = document.createElement('div');
      stack.className = 'offer-card-image-stack';
      stack.setAttribute('role', 'button');
      stack.setAttribute('tabindex', '0');
      stack.setAttribute(
        'aria-label',
        (c.destinationName || 'Destination') + ' - hover to see more photos, click to open full-size gallery'
      );
      stack.setAttribute('data-dest-name', c.destinationName || '');
      stack.setAttribute('data-images', urls.join(IMG_JOIN));
      urls.forEach(function(u, idx) {
        let slide = document.createElement('div');
        slide.className = 'offer-card-slide' + (idx === 0 ? ' is-active' : '');
        if (idx === 0) {
          slide.style.backgroundImage = 'url("' + String(u).replace(/"/g, '') + '")';
          slide.style.backgroundSize = 'cover';
          slide.style.backgroundPosition = 'center';
        } else {
          slide.setAttribute('data-bg', String(u));
        }
        slide.setAttribute('aria-hidden', 'true');
        stack.appendChild(slide);
      });
      li.appendChild(stack);
      let h3 = document.createElement('h3');
      h3.className = 'offer-card-dest';
      h3.textContent = c.destinationName || '';
      li.appendChild(h3);
      let priceMeta = document.createElement('p');
      priceMeta.className = 'offer-card-meta';
      priceMeta.textContent = 'Economy - from GBP ' + (c.priceGbp != null ? c.priceGbp : '');
      li.appendChild(priceMeta);
      let btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'offer-card-cta';
      if (c.bookAirport) btn.setAttribute('data-book-airport', c.bookAirport);
      btn.textContent = 'Book now';
      li.appendChild(btn);
      ul.appendChild(li);
    });
    if (!offerDataCards.length) {
      let li = document.createElement('li');
      li.className = 'offer-card';
      let emptyStateMeta = document.createElement('p');
      emptyStateMeta.className = 'offer-card-meta';
      emptyStateMeta.textContent =
        'Fares are not available for this departure airport. Please choose another airport or contact reservations.';
      li.appendChild(emptyStateMeta);
      ul.appendChild(li);
    }
    wireOfferCardImageStacks(ul);
    scheduleOfferImagePreload(ul);
  }
  /** Refreshes offers when the origin changes. */
  function initOffersFromLeavingFrom() {
    let fromInput = document.getElementById('from');
    let departInput = document.getElementById('depart');
    let section = document.querySelector('.latest-offers');
    if (!fromInput || !section) return;

    let debounceId;
    let lastOfferFetchKey =
      (section.getAttribute('data-ssr-origin') || 'MAN').trim() +
      '|' +
      (section.getAttribute('data-ssr-label') || 'Manchester').trim();

    function fetchOffers(originCode, originLabel) {
      let departInput = document.getElementById('depart');
      let depart = (departInput && departInput.value || '').trim();
      let key = originCode + '|' + originLabel + '|' + depart;
      if (key === lastOfferFetchKey) return;
      let requestUrl =
        '/api/latest-offers?origin=' +
        encodeURIComponent(originCode) +
        '&originLabel=' +
        encodeURIComponent(originLabel);
      if (depart) {
        requestUrl += '&depart=' + encodeURIComponent(depart);
      }
      fetch(requestUrl)
        .then(function(r) { return r.json(); })
        .then(function(data) {
          lastOfferFetchKey = key;
          renderOffersFromJson(data);
        })
        .catch(function() {});
    }

    function sync() {
      let fromValue = (fromInput.value || '').trim();
      let fallbackCode = (section.getAttribute('data-ssr-origin') || 'MAN').trim();
      let fallbackLabel = (section.getAttribute('data-ssr-label') || 'Manchester').trim();

      if (!fromValue) {
        fetchOffers(fallbackCode, fallbackLabel);
        return;
      }

      let iataMatch = fromValue.match(/\(([A-Z]{3})\)\s*$/);
      if (!iataMatch) return;

      let code = iataMatch[1];
      let display = fromValue.replace(/\s*\([A-Z]{3}\)\s*$/, '').trim() || fromValue;
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
        let fromValue = (fromInput.value || '').trim();
        if (/\([A-Z]{3}\)\s*$/.test(fromValue)) sync();
      }, 400);
    });

    if (departInput) {
      departInput.addEventListener('change', sync);
      departInput.addEventListener('input', sync);
    }

    if ((fromInput.value || '').trim()) sync();
  }
  /** Uses an offer card as the destination search. */
  function initBookNowFromOffers() {
    let ul = document.getElementById('offers-cards');
    if (!ul) return;
    ul.addEventListener('click', function(event) {
      let button = event.target.closest('.offer-card-cta');
      if (!button) return;
      event.preventDefault();
      let selectedAirport = button.getAttribute('data-book-airport');
      if (!selectedAirport) return;
      let toInput = document.getElementById('to');
      if (!toInput) return;
      toInput.value = selectedAirport;
      toInput.dispatchEvent(new Event('change', { bubbles: true }));
      let err = document.getElementById('flight-search-error');
      if (err) {
        err.setAttribute('hidden', '');
        err.textContent = '';
      }
      let form = document.querySelector('.flight-search-form');
      let fromInput = document.getElementById('from');
      let departInput = document.getElementById('depart');
      let hasSearchRequirements =
        form &&
        fromInput &&
        departInput &&
        (fromInput.value || '').trim() &&
        (departInput.value || '').trim();
      if (hasSearchRequirements) {
        if (typeof form.requestSubmit === 'function') {
          form.requestSubmit();
        } else {
          form.submit();
        }
        return;
      }
      let searchSection = document.querySelector('.flight-search');
      if (searchSection && typeof searchSection.scrollIntoView === 'function') {
        searchSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
      if (!fromInput || !(fromInput.value || '').trim()) {
        if (fromInput) fromInput.focus();
        return;
      }
      let departTrigger = document.getElementById('depart-trigger');
      if (departTrigger) departTrigger.focus();
    });
  }
  /** Offer image lightbox. */
  function initOfferLightbox() {
    let ul = document.getElementById('offers-cards');
    let lb = document.getElementById('offer-lightbox');
    if (!ul || !lb) return;
    let imgEl = document.getElementById('offer-lightbox-img');
    let titleEl = document.getElementById('offer-lightbox-title');
    let capEl = document.getElementById('offer-lightbox-caption');
    let ctrEl = document.getElementById('offer-lightbox-counter');
    let prevBtn = document.getElementById('offer-lightbox-prev');
    let nextBtn = document.getElementById('offer-lightbox-next');
    let closeBtn = lb.querySelector('.offer-lightbox__close');
    let state = { images: [], idx: 0, name: '' };

    function closeLb() {
      lb.setAttribute('hidden', '');
      document.body.style.overflow = '';
      if (imgEl) imgEl.removeAttribute('src');
      state.images = [];
    }

    function showLb(targetIndex) {
      if (!state.images.length || !imgEl || !titleEl || !capEl || !ctrEl) return;
      let imageCount = state.images.length;
      state.idx = ((targetIndex % imageCount) + imageCount) % imageCount;
      imgEl.src = state.images[state.idx];
      imgEl.alt = state.name + ' — photo ' + (state.idx + 1);
      titleEl.textContent = state.name;
      capEl.textContent = state.name + ' · photo ' + (state.idx + 1) + ' of ' + imageCount;
      ctrEl.textContent = state.idx + 1 + ' / ' + imageCount;
    }

    function openLb(stack) {
      let images = parseImagesFromStack(stack);
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
      let stack = e.target.closest('.offer-card-image-stack');
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
      let stack = e.target.closest('.offer-card-image-stack');
      if (!stack || (e.key !== 'Enter' && e.key !== ' ')) return;
      e.preventDefault();
      openLb(stack);
    });
  }
  /** Offer carousel controls. */
  function initOffersCarousel() {
    let scrollEl = document.getElementById('offers-scroll');
    let prev = document.getElementById('offers-prev');
    let next = document.getElementById('offers-next');
    if (!scrollEl) return;

    scrollEl.addEventListener('wheel', function(e) {
      if (scrollEl.scrollWidth <= scrollEl.clientWidth) return;
      let dx = e.deltaX;
      let dy = e.deltaY;
      if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 0.5) {
        e.preventDefault();
        return;
      }
      if (e.shiftKey && Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 0.5) {
        e.preventDefault();
      }
    }, { passive: false });

    function offerCards() {
      let ul = scrollEl.querySelector('.offer-cards');
      if (!ul) return [];
      return ul.querySelectorAll(':scope > .offer-card');
    }

    function nearestCardIndex(cards) {
      let left = scrollEl.scrollLeft;
      let best = 0;
      let bestDist = Number.POSITIVE_INFINITY;
      cards.forEach(function(card, idx) {
        let distance = Math.abs(card.offsetLeft - left);
        if (distance < bestDist) {
          bestDist = distance;
          best = idx;
        }
      });
      return best;
    }

    function jumpByCards(direction) {
      const carouselCards = offerCards();
      if (!carouselCards.length) return;
      if (carouselCards.length <= 4) {
        scrollEl.scrollBy({ left: direction * (scrollEl.clientWidth || 280), behavior: 'smooth' });
        return;
      }
      const current = nearestCardIndex(carouselCards);
      const target = Math.max(0, Math.min(carouselCards.length - 1, current + (direction * 4)));
      scrollEl.scrollTo({ left: carouselCards[target].offsetLeft, behavior: 'smooth' });
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

  function onReady() {
    initAutocomplete('from', 'from-list', 'to');
    initAutocomplete('to', 'to-list', 'from');
    initTripCombobox();
    initCabinModal();
    initOffersCarousel();
    initOffersFromLeavingFrom();
    initBookNowFromOffers();
    let offersCards = document.getElementById('offers-cards');
    wireOfferCardImageStacks(offersCards);
    scheduleOfferImagePreload(offersCards);
    initOfferLightbox();
    initDatePickers();
    initFlightSearchValidation();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', onReady);
  } else {
    onReady();
  }
})();
