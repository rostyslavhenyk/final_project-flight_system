// FAQ accordion
var faqButtons = document.querySelectorAll('.faq-question')

for (var i = 0; i < faqButtons.length; i++) {
    faqButtons[i].addEventListener('click', function () {
        var answerId = this.getAttribute('aria-controls')
        var answer = document.getElementById(answerId)

        if (answer.hidden) {
            answer.hidden = false
            this.setAttribute('aria-expanded', 'true')
        } else {
            answer.hidden = true
            this.setAttribute('aria-expanded', 'false')
        }
    })
}

// refund tabs
function switchTab(tabName) {
    document.getElementById('track').hidden = true
    document.getElementById('request').hidden = true

    document.getElementById('tab-track').classList.remove('active')
    document.getElementById('tab-request').classList.remove('active')
    document.getElementById('tab-track').setAttribute('aria-selected', 'false')
    document.getElementById('tab-request').setAttribute('aria-selected', 'false')

    document.getElementById(tabName).hidden = false
    document.getElementById('tab-' + tabName).classList.add('active')
    document.getElementById('tab-' + tabName).setAttribute('aria-selected', 'true')
}

// FAQ search
document.getElementById('faqSearch').addEventListener('input', function () {
    var typed = this.value.toLowerCase()
    var allItems = document.querySelectorAll('.faq-item')

    for (var i = 0; i < allItems.length; i++) {
        var questionText = allItems[i].querySelector('.faq-question').textContent.toLowerCase()

        if (questionText.includes(typed)) {
            allItems[i].style.display = 'block'
        } else {
            allItems[i].style.display = 'none'
        }
    }
})

// chat open and close
function toggleChat() {
    var chatBody = document.getElementById('chatBody')
    var chatHeader = document.querySelector('.chat-header')
    var chevron = document.getElementById('chatChevron')

    if (chatBody.hidden) {
        chatBody.hidden = false
        chatHeader.setAttribute('aria-expanded', 'true')
        chevron.textContent = '▼'
        document.getElementById('chatInput').focus()
    } else {
        chatBody.hidden = true
        chatHeader.setAttribute('aria-expanded', 'false')
        chevron.textContent = '▲'
    }
}

// keyword based bot replies
function getBotReply(text) {
    var msg = text.toLowerCase()

    if (msg.includes('refund')) {
        return 'For refund requests, please use the Refunds section on this page or email us at support@glideairways.com. Refunds are processed within 3-5 business days.'
    }

    if (msg.includes('book') || msg.includes('booking')) {
        return 'To manage your booking, go to the Manage Booking page. You can change or cancel flights there as long as they have not departed yet.'
    }

    if (msg.includes('check in') || msg.includes('checkin') || msg.includes('check-in')) {
        return 'Online check-in opens 24 hours before your flight. Go to the Check-in page and have your booking reference and passport ready.'
    }

    if (msg.includes('baggage') || msg.includes('luggage') || msg.includes('bag')) {
        return 'Standard economy includes 1 carry-on bag up to 10kg. Extra baggage can be added through Manage Booking before your flight departs.'
    }

    if (msg.includes('delay') || msg.includes('delayed') || msg.includes('cancel') || msg.includes('cancelled')) {
        return 'If your flight is delayed or cancelled you will be notified by email. You can also check live status on the Flight Status page. You may be entitled to compensation - raise a refund request below.'
    }

    if (msg.includes('membership') || msg.includes('points') || msg.includes('loyalty')) {
        return 'We offer Silver, Gold and Platinum membership tiers. Visit the Membership page to sign up for free and start earning points from your first booking.'
    }

    if (msg.includes('passport') || msg.includes('visa') || msg.includes('document')) {
        return 'You will need a valid passport or government ID and your booking reference to check in. For visa requirements check your destination country\'s official embassy website.'
    }

    if (msg.includes('seat') || msg.includes('seats')) {
        return 'Seat selection is available during booking. You can also update your seat through Manage Booking before your flight departs.'
    }

    if (msg.includes('payment') || msg.includes('pay') || msg.includes('price') || msg.includes('cost')) {
        return 'We accept all major credit and debit cards. If you have a payment issue please contact us at support@glideairways.com.'
    }

    if (msg.includes('hello') || msg.includes('hi') || msg.includes('hey')) {
        return 'Hi there! How can we help you today? You can ask about bookings, check-in, baggage, refunds, or anything else.'
    }

    if (msg.includes('thank') || msg.includes('thanks')) {
        return 'You\'re welcome! Is there anything else we can help you with?'
    }

    return 'Thanks for your message. For urgent queries please email us at support@glideairways.com or call +44 000 000 0000 Monday to Friday 8am-8pm.'
}

// send chat message
function sendChat() {
    var input = document.getElementById('chatInput')
    var messages = document.getElementById('chatMessages')
    var text = input.value.trim()

    if (text === '') return

    var userMsg = document.createElement('div')
    userMsg.classList.add('chat-msg', 'user')
    userMsg.textContent = text
    messages.appendChild(userMsg)
    input.value = ''
    messages.scrollTop = messages.scrollHeight

    fetch('/chat/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'message=' + encodeURIComponent(text)
    }).catch(function () {
        // not logged in, just show bot reply
    })

    var typing = document.createElement('div')
    typing.classList.add('chat-msg', 'bot', 'typing-indicator')
    typing.textContent = '...'
    messages.appendChild(typing)
    messages.scrollTop = messages.scrollHeight

    setTimeout(function () {
        messages.removeChild(typing)

        var botMsg = document.createElement('div')
        botMsg.classList.add('chat-msg', 'bot')
        botMsg.textContent = getBotReply(text)
        messages.appendChild(botMsg)
        messages.scrollTop = messages.scrollHeight
    }, 1000)
}

// loads staff replies from backend
function loadReplies() {
    fetch('/chat/messages')
        .then(function (response) {
            if (!response.ok) return
            return response.json()
        })
        .then(function (data) {
            if (!data) return

            var messages = document.getElementById('chatMessages')
            messages.innerHTML = ''

            for (var i = 0; i < data.length; i++) {
                var msg = document.createElement('div')
                msg.classList.add('chat-msg')
                msg.classList.add(data[i].isStaff ? 'bot' : 'user')
                msg.textContent = data[i].message
                messages.appendChild(msg)
            }

            messages.scrollTop = messages.scrollHeight
        })
        .catch(function () {
            // not logged in, just ignore
        })
}

// load replies when chat opens
function openChat() {
    var chatBody = document.getElementById('chatBody')
    if (chatBody.hidden) {
        toggleChat()
    }
    loadReplies()
}

function handleChatKey(event) {
    if (event.key === 'Enter') {
        sendChat()
    }
}

// form placeholder responses
function trackRefund(event) {
    event.preventDefault()
    document.getElementById('trackResult').innerHTML = '<p class="form-success">Your refund is being processed and should arrive within 3-5 business days.</p>'
}

function submitRefund(event) {
    event.preventDefault()

    var firstname = document.getElementById('refund-firstname').value
    var lastname = document.getElementById('refund-lastname').value
    var email = document.getElementById('refund-email').value
    var ref = document.getElementById('refund-ref').value
    var reason = document.getElementById('refund-reason').value
    var details = document.getElementById('refund-details').value

    var body = 'firstname=' + encodeURIComponent(firstname) +
        '&lastname=' + encodeURIComponent(lastname) +
        '&email=' + encodeURIComponent(email) +
        '&ref=' + encodeURIComponent(ref) +
        '&reason=' + encodeURIComponent(reason) +
        '&details=' + encodeURIComponent(details)

    fetch('/help/refund', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body
    })
        .then(function (response) {
            if (response.ok) {
                document.getElementById('requestResult').innerHTML = '<p class="form-success">Your request has been submitted. You will receive a confirmation email shortly.</p>'
            } else {
                document.getElementById('requestResult').innerHTML = '<p style="color:#d32f2f;">Something went wrong. Please try again.</p>'
            }
        })
}

function submitContact(event) {
    event.preventDefault()
    document.getElementById('contactResult').innerHTML = '<p class="form-success">Message sent! We will get back to you within 24 hours.</p>'
}
