/**
 * /book/payment - confirms a Stripe test-mode card setup, then logs the paid booking server-side.
 */
(function () {
  'use strict';

  const STORAGE_PAX = 'glideBookingPaxNames';
  const stripeState = {
    stripe: null,
    card: null,
    clientSecret: '',
    ready: false,
  };

  function redirectToManageBooking() {
    window.location.href = '/my-account#manage-booking';
  }

  function wirePayNow() {
    const btn = document.getElementById('pay-now-button');
    if (!btn) return;
    btn.addEventListener('click', function () {
      btn.disabled = true;
      const originalText = btn.textContent;
      btn.textContent = 'Checking card...';
      confirmStripeCard()
        .then(function (setupIntentId) {
          btn.textContent = 'Processing...';
          return confirmBooking(setupIntentId);
        })
        .then(function (response) {
          if (!response.ok) throw new Error('Payment confirmation failed');
          redirectToManageBooking();
        })
        .catch(function () {
          btn.disabled = false;
          btn.textContent = originalText;
          showPaymentError('Card setup or booking confirmation failed. Please check the card and seats.');
        });
    });
  }

  function confirmBooking(setupIntentId) {
    const body = new URLSearchParams();
    body.set('setupIntentId', setupIntentId);
    return fetch('/book/payment/confirm' + window.location.search, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: body.toString(),
    });
  }

  function confirmStripeCard() {
    if (!stripeState.ready || !stripeState.stripe || !stripeState.card || !stripeState.clientSecret) {
      return Promise.reject(new Error('Stripe is not ready'));
    }
    return stripeState.stripe
      .confirmCardSetup(stripeState.clientSecret, {
        payment_method: {
          card: stripeState.card,
        },
      })
      .then(function (result) {
        if (result.error) throw new Error(result.error.message || 'Card setup failed');
        if (!result.setupIntent || result.setupIntent.status !== 'succeeded') {
          throw new Error('Card setup was not completed');
        }
        return result.setupIntent.id;
      });
  }

  function showPaymentError(message) {
    const btn = document.getElementById('pay-now-button');
    if (!btn || !btn.parentNode) return;
    const existing = document.getElementById('pay-confirm-error');
    if (existing) {
      existing.hidden = false;
      if (message) existing.textContent = message;
      return;
    }
    const msg = document.createElement('p');
    msg.id = 'pay-confirm-error';
    msg.className = 'flights-hero__hint';
    msg.setAttribute('role', 'alert');
    msg.textContent = message || 'Card setup or booking confirmation failed. Please check the card and seats.';
    btn.insertAdjacentElement('afterend', msg);
  }

  function initStripeCard() {
    const cardEl = document.getElementById('stripe-card-element');
    if (!cardEl || typeof Stripe !== 'function') return Promise.resolve();
    return fetch('/book/payment/setup-intent' + window.location.search, { method: 'POST' })
      .then(function (response) {
        if (!response.ok) throw new Error('Stripe test keys are not configured');
        return response.json();
      })
      .then(function (payload) {
        stripeState.stripe = Stripe(payload.publishableKey);
        const elements = stripeState.stripe.elements();
        stripeState.card = elements.create('card', {
          hidePostalCode: true,
        });
        stripeState.card.mount(cardEl);
        stripeState.clientSecret = payload.clientSecret;
        stripeState.ready = true;
      })
      .catch(function () {
        showPaymentError(
          'Stripe test mode is not configured. Add GLIDE_STRIPE_SECRET_KEY and GLIDE_STRIPE_PUBLISHABLE_KEY.',
        );
      });
  }

  function hydratePaxNames() {
    try {
      const raw = sessionStorage.getItem(STORAGE_PAX);
      if (!raw) return;
      const list = JSON.parse(raw);
      if (!Array.isArray(list)) return;
      document.querySelectorAll('[data-pay-pax-slot]').forEach(function (cell) {
        const slotStr = cell.getAttribute('data-pay-pax-slot');
        if (!slotStr) return;
        const slot = parseInt(slotStr, 10);
        const el = cell.querySelector('[data-pay-pax-name-text]');
        if (!el) return;
        for (let i = 0; i < list.length; i++) {
          const entry = list[i];
          if (!entry || parseInt(entry.slot, 10) !== slot) continue;
          const name = String(entry.displayName || '')
            .replace(/\s+/g, ' ')
            .trim();
          if (name) el.textContent = name;
          break;
        }
      });
    } catch (e) {}
  }

  function init() {
    hydratePaxNames();
    initStripeCard();
    wirePayNow();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
