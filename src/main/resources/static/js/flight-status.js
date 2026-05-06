(function() {
  'use strict';

  const FORCE_SELECT_MS = 1000;
  const STATUS_DATE_PICKERS = new WeakMap();

  function initAutocomplete(inputId, listId) {
    let input = document.getElementById(inputId);
    let list = document.getElementById(listId);
    if (!input || !list) return;
    let options = list.querySelectorAll('[role="option"]');

    function hide() {
      list.classList.remove('is-open');
      input.setAttribute('aria-expanded', 'false');
    }

    function choose(value) {
      input.value = value;
      hide();
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function filter() {
      let q = (input.value || '').trim().toLowerCase();
      let matched = [];
      let emptyEl = list.querySelector('.autocomplete-dropdown__empty');
      options.forEach(function(el) {
        let value = (el.getAttribute('data-value') || '').trim();
        let ok = !q || value.toLowerCase().indexOf(q) === 0;
        el.style.display = ok ? 'block' : 'none';
        if (ok) matched.push(el);
      });
      if (emptyEl) {
        let showEmpty = q.length > 0 && matched.length === 0;
        emptyEl.hidden = !showEmpty;
      }
      list.classList.add('is-open');
      input.setAttribute('aria-expanded', 'true');
    }

    input.addEventListener('focus', filter);
    input.addEventListener('input', filter);
    input.addEventListener('blur', function() { setTimeout(hide, 150); });
    list.querySelectorAll('[role="option"]').forEach(function(el) {
      el.addEventListener('mousedown', function(e) {
        e.preventDefault();
        choose(el.getAttribute('data-value'));
      });
    });
    if (typeof window.attachForceSelectAll === 'function') {
      window.attachForceSelectAll(input, { forceSelectMs: FORCE_SELECT_MS });
    }
  }

  function initStatusDatePickers() {
    let flatpickrFactory = window['flatpickr'];
    if (typeof flatpickrFactory !== 'function') return;
    document.querySelectorAll('.status-date-picker').forEach(function(input) {
      let minDate = input.getAttribute('data-min-date') || 'today';
      STATUS_DATE_PICKERS.set(input, flatpickrFactory(input, {
        mode: 'single',
        dateFormat: 'Y-m-d',
        minDate: minDate
      }));
    });
  }

  function initFlightNumberField() {
    let input = document.getElementById('status-flight-digits') || document.querySelector('.status-input--flight-digits');
    let list = document.getElementById('status-flight-suggest-list');
    if (!input) return;
    let debounceTimer = null;
    let emptyMsg = null;
    let suggestSeq = 0;

    let maxFlightDigits =
      parseInt(input.getAttribute('data-max-flight-digits') || '6', 10);
    if (isNaN(maxFlightDigits) || maxFlightDigits < 1) {
      maxFlightDigits = 6;
    }

    function onlyDigits() {
      let d = (input.value || '').replace(/\D/g, '').slice(0, maxFlightDigits);
      if (input.value !== d) {
        input.value = d;
      }
      return d;
    }

    function clearOptions() {
      if (!list) return;
      list.querySelectorAll('[role="option"]').forEach(function(el) {
        el.remove();
      });
    }

    function setOpen(isOpen) {
      if (!list) return;
      if (isOpen) {
        list.classList.add('is-open');
      } else {
        list.classList.remove('is-open');
      }
      input.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
    }

    function showSuggestions() {
      let digits = onlyDigits();
      if (!list) {
        return;
      }
      if (!digits || digits.length < 1) {
        clearOptions();
        if (emptyMsg) {
          emptyMsg.remove();
          emptyMsg = null;
        }
        setOpen(false);
        suggestSeq++;
        return;
      }
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(function() {
        let mySeq = ++suggestSeq;
        fetch('/api/flight-number-suggest?q=' + encodeURIComponent(digits))
          .then(function(r) {
            if (!r.ok) throw new Error('bad');
            return r.json();
          })
          .then(function(arr) {
            if (mySeq !== suggestSeq) {
              return;
            }
            clearOptions();
            if (emptyMsg) {
              emptyMsg.remove();
              emptyMsg = null;
            }
            if (!arr || arr.length === 0) {
              emptyMsg = document.createElement('p');
              emptyMsg.className = 'autocomplete-dropdown__empty';
              emptyMsg.setAttribute('role', 'status');
              emptyMsg.textContent = "This flight number was not found.";
              list.appendChild(emptyMsg);
              setOpen(true);
              return;
            }
            arr.forEach(function(suffix) {
              let el = document.createElement('div');
              el.setAttribute('role', 'option');
              el.setAttribute('data-value', suffix);
              el.textContent = 'GA' + suffix;
              el.addEventListener('mousedown', function(e) {
                e.preventDefault();
                suggestSeq++;
                clearTimeout(debounceTimer);
                input.value = suffix;
                setOpen(false);
                clearOptions();
                if (emptyMsg) {
                  emptyMsg.remove();
                  emptyMsg = null;
                }
                onlyDigits();
                input.dispatchEvent(new Event('change', { bubbles: true }));
              });
              list.appendChild(el);
            });
            setOpen(true);
          })
          .catch(function() {
            if (mySeq === suggestSeq) {
              setOpen(false);
            }
          });
      }, 200);
    }

    input.addEventListener('input', showSuggestions);
    input.addEventListener('focus', showSuggestions);
    input.addEventListener('blur', function() {
      setTimeout(function() {
        setOpen(false);
      }, 150);
    });
    onlyDigits();
    if (typeof window.attachForceSelectAll === 'function') {
      window.attachForceSelectAll(input, { forceSelectMs: FORCE_SELECT_MS });
    }
  }

  function initAnyDateToggle() {
    let toggles = document.querySelectorAll('input[name="anyDate"]');
    if (!toggles.length) return;
    let dateInputs = document.querySelectorAll('.status-date-picker');

    function sync() {
      let enabled = false;
      toggles.forEach(function(t) { if (t.checked) enabled = true; });
      dateInputs.forEach(function(input) {
        let picker = STATUS_DATE_PICKERS.get(input);
        input.disabled = enabled;
        if (picker) {
          if (enabled) picker.close();
          picker.set('clickOpens', !enabled);
        }
      });
    }

    toggles.forEach(function(t) { t.addEventListener('change', sync); });
    sync();
  }

  function onReady() {
    initStatusDatePickers();
    initFlightNumberField();
    initAutocomplete('status-from', 'status-from-list');
    initAutocomplete('status-to', 'status-to-list');
    initAnyDateToggle();
    document.querySelectorAll('.status-page .status-form .status-input--date').forEach(function(el) {
      if (typeof window.attachForceSelectAll === 'function') {
        window.attachForceSelectAll(el, { forceSelectMs: FORCE_SELECT_MS });
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', onReady);
  } else {
    onReady();
  }
})();
