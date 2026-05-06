/** Shared select-all-on-focus helper for filled text inputs. */
(function(global) {
  'use strict';

  global.attachForceSelectAll = function(input, opts) {
    opts = opts || {};
    var forceSelectMs = opts.forceSelectMs != null ? opts.forceSelectMs : 800;
    if (
      !input ||
      input.getAttribute('data-force-select') === '0' ||
      input.getAttribute('data-status-force-select') === '0'
    ) {
      return;
    }

    var forceSelectUntil = 0;
    var forceSelectIntervalId = null;

    function clearForceSelectAllWindow() {
      forceSelectUntil = 0;
      if (forceSelectIntervalId !== null) {
        clearInterval(forceSelectIntervalId);
        forceSelectIntervalId = null;
      }
    }

    function selectAll() {
      var len = (input.value || '').length;
      try {
        input.setSelectionRange(0, len);
      } catch (e) {
        try {
          input.select();
        } catch (e2) {
          /* e.g. type=number or hidden */
        }
      }
    }

    function startForceSelectAllWindow() {
      clearForceSelectAllWindow();
      forceSelectUntil = Date.now() + forceSelectMs;
      forceSelectIntervalId = setInterval(function() {
        if (Date.now() < forceSelectUntil) {
          selectAll();
        } else {
          clearForceSelectAllWindow();
        }
      }, 40);
    }

    function selectAllAfterPaint() {
      selectAll();
    }

    input.addEventListener('focus', function() {
      if (!(input.value || '').trim()) {
        return;
      }
      startForceSelectAllWindow();
      if (typeof window.requestAnimationFrame === 'function') {
        window.requestAnimationFrame(function() {
          window.requestAnimationFrame(selectAllAfterPaint);
        });
      } else {
        setTimeout(selectAllAfterPaint, 0);
      }
    });

    input.addEventListener('mouseup', function(e) {
      if (Date.now() < forceSelectUntil) {
        e.preventDefault();
      }
    });

    input.addEventListener('blur', function() {
      clearForceSelectAllWindow();
    });
  };
})(typeof window !== 'undefined' ? window : this);
