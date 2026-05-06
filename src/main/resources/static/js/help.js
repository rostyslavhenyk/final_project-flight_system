// FAQ accordion
const FAQ_BUTTONS = document.querySelectorAll('.faq-question')

for (let i = 0; i < FAQ_BUTTONS.length; i++) {
    FAQ_BUTTONS[i].addEventListener('click', function() {
        let answerId = this.getAttribute('aria-controls')
        let answer = document.getElementById(answerId)

        if (answer.hidden) {
            answer.hidden = false
            this.setAttribute('aria-expanded', 'true')
        } else {
            answer.hidden = true
            this.setAttribute('aria-expanded', 'false')
        }
    })
}

// Refund tabs
window.switchTab = function(tabName) {
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
document.getElementById('faqSearch').addEventListener('input', function() {
    let typed = this.value.toLowerCase()
    let allItems = document.querySelectorAll('.faq-item')

    for (let i = 0; i < allItems.length; i++) {
        let questionText = allItems[i].querySelector('.faq-question').textContent.toLowerCase()

        if (questionText.includes(typed)) {
            allItems[i].style.display = 'block'
        } else {
            allItems[i].style.display = 'none'
        }
    }
})

// Chat toggle
window.toggleChat = function() {
    let chatBody = document.getElementById('chatBody')
    let chatHeader = document.querySelector('.chat-header')
    let chevron = document.getElementById('chatChevron')

    if (chatBody.hidden) {
        chatBody.hidden = false
        chatHeader.setAttribute('aria-expanded', 'true')
        chevron.textContent = 'v'
        document.getElementById('chatInput').focus()
    } else {
        chatBody.hidden = true
        chatHeader.setAttribute('aria-expanded', 'false')
        chevron.textContent = '^'
    }
}

window.openChat = function() {
    let chatBody = document.getElementById('chatBody')
    if (chatBody.hidden) {
        window.toggleChat()
    }
}

function getBotReply(text) {
    let msg = text.toLowerCase()

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

window.sendChat = function() {
    let input = document.getElementById('chatInput')
    let messages = document.getElementById('chatMessages')
    let text = input.value.trim()

    if (text === '') return

    let userMsg = document.createElement('div')
    userMsg.classList.add('chat-msg', 'user')
    userMsg.textContent = text
    messages.appendChild(userMsg)
    input.value = ''
    messages.scrollTop = messages.scrollHeight

    let typing = document.createElement('div')
    typing.classList.add('chat-msg', 'bot', 'typing-indicator')
    typing.textContent = '...'
    messages.appendChild(typing)
    messages.scrollTop = messages.scrollHeight

    setTimeout(function() {
        messages.removeChild(typing)

        let botMsg = document.createElement('div')
        botMsg.classList.add('chat-msg', 'bot')
        botMsg.textContent = getBotReply(text)
        messages.appendChild(botMsg)
        messages.scrollTop = messages.scrollHeight
    }, 1000)
}

window.handleChatKey = function(event) {
    if (event.key === 'Enter') {
        window.sendChat()
    }
}

// Form placeholders
window.trackRefund = function(event) {
    event.preventDefault()
    document.getElementById('trackResult').innerHTML = '<p class="form-success">Your refund is being processed and should arrive within 3-5 business days.</p>'
}

window.submitRefund = function(event) {
    event.preventDefault()
    document.getElementById('requestResult').innerHTML = '<p class="form-success">Your request has been submitted. You will receive a confirmation email shortly.</p>'
}

window.submitContact = function(event) {
    event.preventDefault()
    document.getElementById('contactResult').innerHTML = '<p class="form-success">Message sent! We will get back to you within 24 hours.</p>'
}
