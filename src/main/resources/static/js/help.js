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
        allItems[i].style.display = questionText.includes(typed) ? 'block' : 'none'
    }
})

var chatPollId = null
var chatPollMs = 8000

// chat open and close
function toggleChat() {
    var chatBody = document.getElementById('chatBody')
    var chatHeader = document.querySelector('.chat-header')
    var chevron = document.getElementById('chatChevron')

    if (chatBody.hidden) {
        chatBody.hidden = false
        chatHeader.setAttribute('aria-expanded', 'true')
        chevron.textContent = 'v'
        document.getElementById('chatInput').focus()
        loadReplies()
        startChatPolling()
    } else {
        chatBody.hidden = true
        chatHeader.setAttribute('aria-expanded', 'false')
        chevron.textContent = '^'
        stopChatPolling()
    }
}

// send chat message
function sendChat() {
    var input = document.getElementById('chatInput')
    var text = input.value.trim()

    if (text === '') return

    input.value = ''

    fetch('/chat/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'message=' + encodeURIComponent(text)
    })
        .then(function (response) {
            if (response.status === 401) {
                renderChatStatus('Log in to send a live chat message to staff.')
                return
            }

            if (!response.ok) {
                renderChatStatus('Your message could not be sent. Please try again.')
                return
            }

            loadReplies()
        })
        .catch(function () {
            renderChatStatus('Your message could not be sent. Please try again.')
        })
}

// loads customer and staff messages from backend
function loadReplies() {
    fetch('/chat/messages')
        .then(function (response) {
            if (response.status === 401) {
                renderChatStatus('Log in to start a live chat with staff.')
                return
            }

            if (!response.ok) return
            return response.json()
        })
        .then(function (data) {
            if (!data) return
            renderChatMessages(data)
        })
        .catch(function () {
            renderChatStatus('Live chat is unavailable right now.')
        })
}

function renderChatMessages(data) {
    var messages = document.getElementById('chatMessages')
    messages.innerHTML = ''

    if (data.length === 0) {
        renderChatStatus('Send a message and staff will reply here.')
        return
    }

    for (var i = 0; i < data.length; i++) {
        var msg = document.createElement('div')
        msg.classList.add('chat-msg')
        msg.classList.add(data[i].isStaff ? 'bot' : 'user')
        msg.textContent = data[i].senderName + ': ' + data[i].message
        messages.appendChild(msg)
    }

    messages.scrollTop = messages.scrollHeight
}

function renderChatStatus(text) {
    var messages = document.getElementById('chatMessages')
    messages.innerHTML = ''

    var status = document.createElement('div')
    status.classList.add('chat-msg', 'system')
    status.textContent = text
    messages.appendChild(status)
}

function startChatPolling() {
    if (chatPollId !== null) return
    chatPollId = window.setInterval(loadReplies, chatPollMs)
}

function stopChatPolling() {
    if (chatPollId === null) return
    window.clearInterval(chatPollId)
    chatPollId = null
}

function openChat() {
    var chatBody = document.getElementById('chatBody')
    if (chatBody.hidden) {
        toggleChat()
    } else {
        loadReplies()
    }
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
    document.getElementById('requestResult').innerHTML = '<p class="form-success">Your request has been submitted. You will receive a confirmation email shortly.</p>'
}

function submitContact(event) {
    event.preventDefault()
    document.getElementById('contactResult').innerHTML = '<p class="form-success">Message sent! We will get back to you within 24 hours.</p>'
}
