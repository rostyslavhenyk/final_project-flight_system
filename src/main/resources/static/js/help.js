// FAQ accordion
var faqButtons = document.querySelectorAll('.faq-question')

for (var i = 0; i < faqButtons.length; i++) {
    faqButtons[i].addEventListener('click', function() {
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

// Refund tabs
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
document.getElementById('faqSearch').addEventListener('input', function() {
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

// Chat toggle
function toggleChat() {
    var chatBody = document.getElementById('chatBody')
    var chatHeader = document.querySelector('.chat-header')
    var chevron = document.getElementById('chatChevron')

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

function openChat() {
    var chatBody = document.getElementById('chatBody')
    if (chatBody.hidden) {
        toggleChat()
    }
}

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

    setTimeout(function() {
        var botMsg = document.createElement('div')
        botMsg.classList.add('chat-msg', 'bot')
        botMsg.textContent = 'Thanks for your message! A member of our team will be with you shortly.'
        messages.appendChild(botMsg)
        messages.scrollTop = messages.scrollHeight
    }, 800)
}

function handleChatKey(event) {
    if (event.key === 'Enter') {
        sendChat()
    }
}

// Form placeholders
function trackRefund(event) {
    event.preventDefault()
    document.getElementById('trackResult').innerHTML = '<p class="form-success">Your refund is being processed and should arrive within 3-5 business days.</p>'
}

function submitRefund(event) {
    event.preventDefault()
    document.getElementById('requestResult').innerHTML = '<p class="form-success">Your request has been submitted. You will receive a confirmation email shortly.</p>'
}

function submitContact(event) {
    if (!event.currentTarget.checkValidity()) return

    document.getElementById('contactResult').innerHTML = '<p class="form-success">Sending...</p>'
}
