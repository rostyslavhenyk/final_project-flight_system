var resetKey = ''
var resetType = ''

function sendResetCode() {
    var email = document.getElementById('reset-email').value.trim()
    var phone = document.getElementById('reset-phone').value.trim()

    if (email === '' && phone === '') {
        document.getElementById('verify-status').textContent = 'Please enter an email or phone number'
        return
    }

    var body = ''
    if (email !== '') {
        resetKey = email
        resetType = 'email'
        body = 'email=' + encodeURIComponent(email)
    } else {
        resetKey = phone
        resetType = 'phone'
        body = 'phone=' + encodeURIComponent(phone)
    }

    fetch('/forgot-password/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body
    })
        .then(function (response) {
            return response.text().then(function (text) {
                if (response.ok) {
                    document.getElementById('step1').hidden = true
                    document.getElementById('step2').hidden = false
                } else {
                    document.getElementById('verify-status').textContent = text
                }
            })
        })
}

function verifyResetCode() {
    var code = document.getElementById('reset-code').value.trim()

    if (code === '') {
        document.getElementById('verify-status').textContent = 'Please enter the code'
        return
    }

    var body = resetType + '=' + encodeURIComponent(resetKey) + '&code=' + encodeURIComponent(code)

    fetch('/forgot-password/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body
    })
        .then(function (response) {
            return response.text().then(function (text) {
                if (response.ok) {
                    document.getElementById('step2').hidden = true
                    document.getElementById('step3').hidden = false
                } else {
                    document.getElementById('verify-status').textContent = text
                }
            })
        })
}

function resetPassword() {
    var newPassword = document.getElementById('new-password').value
    var confirmPassword = document.getElementById('confirm-password').value

    if (newPassword === '' || confirmPassword === '') {
        document.getElementById('verify-status').textContent = 'Please fill in both fields'
        return
    }

    if (newPassword !== confirmPassword) {
        document.getElementById('verify-status').textContent = 'Passwords do not match'
        return
    }

    var body = resetType + '=' + encodeURIComponent(resetKey) +
        '&newPassword=' + encodeURIComponent(newPassword) +
        '&confirmPassword=' + encodeURIComponent(confirmPassword)

    fetch('/forgot-password/reset', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body
    })
        .then(function (response) {
            if (response.ok) {
                window.location.href = '/login'
            } else {
                response.text().then(function (text) {
                    document.getElementById('verify-status').textContent = text
                })
            }
        })
}