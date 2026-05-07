(function () {
  'use strict';

  function setRange(range) {
    document.querySelectorAll('[data-sales-range]').forEach(function (button) {
      var active = button.getAttribute('data-sales-range') === range;
      button.classList.toggle('staff-dashboard-toggle__btn--active', active);
      button.setAttribute('aria-pressed', active ? 'true' : 'false');
    });

    document.querySelectorAll('[data-sales-total]').forEach(function (total) {
      total.hidden = total.getAttribute('data-sales-total') !== range;
    });

    document.querySelectorAll('[data-sales-chart]').forEach(function (chart) {
      chart.hidden = chart.getAttribute('data-sales-chart') !== range;
    });
  }

  function init() {
    document.querySelectorAll('[data-sales-range]').forEach(function (button) {
      button.addEventListener('click', function () {
        setRange(button.getAttribute('data-sales-range') || 'day');
      });
    });
    setRange('day');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
