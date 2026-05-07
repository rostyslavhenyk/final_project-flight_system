(function () {
    'use strict';

    let resetKey = '';
    let resetType = '';

    function byId(id) {
        return document.getElementById(id);
    }

    function setVerifyStatus(text) {
        const status = byId('verify-status');
        if (status) status.textContent = text;
    }

    function updateVerifyStatus(html) {
        const status = byId('verify-status');
        if (status) status.outerHTML = html;
    }

    function responseWasSuccessful(html, successText) {
        return html.indexOf(successText) !== -1;
    }

    function sendResetCode() {
        const emailInput = byId('reset-email');
        const phoneInput = byId('reset-phone');
        if (!emailInput || !phoneInput) return;

        const email = emailInput.value.trim();
        const phone = phoneInput.value.trim();

        if (email === '' && phone === '') {
            setVerifyStatus('Please enter an email or phone number');
            return;
        }

        const body = new URLSearchParams();
        if (email !== '') {
            resetKey = email;
            resetType = 'email';
            body.set('email', email);
        } else {
            resetKey = phone;
            resetType = 'phone';
            body.set('phone', phone);
        }

        fetch('/forgot-password/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString(),
        })
            .then(function (response) {
                return response.text().then(function (text) {
                    updateVerifyStatus(text);
                    if (response.ok && responseWasSuccessful(text, 'Code sent successfully')) {
                        byId('step1').hidden = true;
                        byId('step2').hidden = false;
                    }
                });
            });
    }

    function verifyResetCode() {
        const codeInput = byId('reset-code');
        if (!codeInput) return;

        const code = codeInput.value.trim();
        if (code === '') {
            setVerifyStatus('Please enter the code');
            return;
        }

        const body = new URLSearchParams();
        body.set(resetType, resetKey);
        body.set('code', code);

        fetch('/forgot-password/verify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString(),
        })
            .then(function (response) {
                return response.text().then(function (text) {
                    updateVerifyStatus(text);
                    if (response.ok && responseWasSuccessful(text, 'Code verified')) {
                        byId('step2').hidden = true;
                        byId('step3').hidden = false;
                    }
                });
            });
    }

    function resetPassword() {
        const newPasswordInput = byId('new-password');
        const confirmPasswordInput = byId('confirm-password');
        if (!newPasswordInput || !confirmPasswordInput) return;

        const newPassword = newPasswordInput.value;
        const confirmPassword = confirmPasswordInput.value;

        if (newPassword === '' || confirmPassword === '') {
            setVerifyStatus('Please fill in both fields');
            return;
        }

        if (newPassword !== confirmPassword) {
            setVerifyStatus('Passwords do not match');
            return;
        }

        const body = new URLSearchParams();
        body.set(resetType, resetKey);
        body.set('newPassword', newPassword);
        body.set('confirmPassword', confirmPassword);

        fetch('/forgot-password/reset', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString(),
        })
            .then(function (response) {
                return response.text().then(function (text) {
                    if (response.ok && text === '') {
                        window.location.href = '/login';
                    } else {
                        updateVerifyStatus(text);
                    }
                });
            });
    }

    function init() {
        const sendButton = byId('send-reset-code-button');
        const verifyButton = byId('verify-reset-code-button');
        const resetButton = byId('reset-password-button');

        if (sendButton) sendButton.addEventListener('click', sendResetCode);
        if (verifyButton) verifyButton.addEventListener('click', verifyResetCode);
        if (resetButton) resetButton.addEventListener('click', resetPassword);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
