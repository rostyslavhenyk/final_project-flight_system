# Glossary

Quick reference for technical terms, acronyms, and HCI concepts used throughout COMP2850.

---

## Architecture & Design Patterns

### Server-First Architecture {#server-first}
**Definition**: Architecture where the server generates complete HTML pages and sends them to the browser, rather than sending JavaScript that builds the page client-side.

**Why it matters**: Pages load faster, work without JavaScript, and are accessible by default because the server controls the semantic HTML structure.

**Example**: Ktor renders a Pebble template → sends complete HTML → browser displays immediately.

**See also**: [Progressive Enhancement](#progressive-enhancement), [SSR](#ssr)

---

### Progressive Enhancement {#progressive-enhancement}
**Definition**: Design philosophy where you build a baseline experience that works for everyone (HTML), then add optional layers (CSS for styling, JavaScript for interactivity) that enhance the experience when available.

**Why it matters**: If JavaScript fails to load (network error, corporate firewall, assistive tech incompatibility), users still get full functionality.

**Example**: Form submits via POST/redirect (no-JS baseline) + HTMX intercepts and does AJAX swap (enhanced).

**See also**: [Server-First](#server-first), [No-JS Parity](#no-js-parity)

---

### No-JS Parity {#no-js-parity}
**Definition**: Requirement that every feature must work identically with JavaScript disabled. The user experience may differ (page reload vs. instant swap), but functionality must be identical.

**Why it matters**: Accessibility, resilience, progressive enhancement compliance.

**Example**: Add task button works via form POST → redirect (no-JS) OR HTMX AJAX → fragment swap (JS-enabled).

**See also**: [Progressive Enhancement](#progressive-enhancement)

---

### PRG (Post-Redirect-Get) {#prg}
**Full name**: Post-Redirect-Get pattern

**Definition**: After processing a POST request (form submission), the server returns an HTTP 303 redirect to a GET URL instead of rendering a page directly. This prevents duplicate submissions if the user refreshes.

**How it works**:
1. User submits form → POST `/tasks` (create task)
2. Server validates, saves to database
3. Server returns HTTP 303 redirect to GET `/tasks`
4. Browser follows redirect → GET `/tasks` (displays updated list)
5. User refreshes → safe GET request (no duplicate)

**Used in COMP2850**: Every form POST must use PRG for the no-JS path.

**See also**: [HTTP Methods](#http-methods)

---

### SSR (Server-Side Rendering) {#ssr}
**Full name**: Server-Side Rendering

**Definition**: The server generates the final HTML and sends it to the browser. The browser receives complete markup, not an empty shell that JavaScript fills in.

**Contrast with CSR**: Client-side rendering (CSR) sends minimal HTML + JavaScript bundle → browser runs JS → JS fetches data → JS builds DOM. Slower, accessibility-unfriendly.

**Used in COMP2850**: Ktor + Pebble templates = SSR.

**See also**: [Server-First](#server-first)

---

### Hypermedia {#hypermedia}
**Definition**: Media (HTML) that contains hyperlinks and forms as controls for navigating and manipulating application state. The server tells the client what actions are available via links (`<a>`) and forms (`<form>`).

**Why it matters**: Follows REST/HATEOAS principles. The UI is the API. No separate JSON API needed.

**Used in COMP2850**: We build hypermedia systems following [hypermedia.systems](https://hypermedia.systems/).

**See also**: [HTMX](#htmx), [REST](#rest), [HATEOAS](#hateoas)

---

### REST {#rest}
**Full name**: Representational State Transfer

**Definition**: Architectural style for web services that uses standard HTTP methods (GET, POST, PUT, DELETE) to operate on resources identified by URLs. Key principles include stateless communication, resource-based URLs, and hypermedia as the engine of application state.

**HTTP methods**:
- **GET** `/tasks` - Retrieve resources (safe, idempotent)
- **POST** `/tasks` - Create new resource
- **PUT** `/tasks/123` - Update entire resource
- **DELETE** `/tasks/123` - Remove resource

**Why it matters**: REST principles guide our route design. We use hypermedia (HTML) instead of JSON APIs, following the original REST vision.

**Used in COMP2850**: All routes follow RESTful conventions. GET for reading, POST for creating, DELETE for removing.

**See also**: [HATEOAS](#hateoas), [HTTP Methods](#http-methods), [Hypermedia](#hypermedia)

---

### HATEOAS {#hateoas}
**Full name**: Hypermedia As The Engine Of Application State

**Definition**: REST principle where the server's responses include hyperlinks/forms that tell the client what actions are possible next. The client doesn't need hardcoded URLs.

**Example**: Task list HTML includes `<form action="/tasks" method="post">` (server tells client "you can add tasks here").

**See also**: [Hypermedia](#hypermedia)

---

## HTMX Concepts

### HTMX {#htmx}
**Definition**: JavaScript library that extends HTML with attributes (`hx-get`, `hx-post`, `hx-target`, `hx-swap`) to enable AJAX requests and DOM updates without writing JavaScript.

**Key idea**: Server sends HTML fragments (not JSON), HTMX swaps them into the page.

**Example**: `<button hx-get="/tasks/123" hx-target="#content">Load</button>` → Click → AJAX GET → Server returns HTML → HTMX replaces `#content`.

**Why it matters**: Simpler than React/Vue, accessible by default, progressive enhancement friendly.

**Documentation**: [htmx.org](https://htmx.org/) | [hypermedia.systems](https://hypermedia.systems/)

**See also**: [AJAX](#ajax), [OOB Swap](#oob-swap), [Progressive Enhancement](#progressive-enhancement)

---

### Fragment {#fragment}
**Definition**: A partial HTML snippet (e.g., `<li>New item</li>` or `<div>...</div>`) returned by the server, rather than a complete page with `<html>`, `<head>`, `<body>`.

**Used in COMP2850**: When HTMX makes a request, the server detects `HX-Request: true` header and returns a fragment instead of the full page.

**Example**: Server renders `tasks/_item.peb` (just the `<li>`) instead of `tasks/index.peb` (full page).

**See also**: [HTMX](#htmx), [Template Factoring](#template-factoring)

---

### OOB Swap (Out-of-Band Swap) {#oob-swap}
**Full name**: Out-of-Band Swap

**Definition**: HTMX feature that updates an element **outside** the main target. The server includes `hx-swap-oob="true"` on an element, and HTMX updates it based on its `id`, even if it's not the primary target.

**Why it matters**: Lets you update multiple parts of the page from one response (e.g., update task list + update status message).

**Example**:
```html
<!-- Main target -->
<li id="task-3">Updated task</li>

<!-- OOB swap (updates #status even though target is #task-list) -->
<div id="status" hx-swap-oob="true">Task added!</div>
```

**See also**: [HTMX](#htmx), [ARIA Live Region](#aria-live-region)

---

### hx-target {#hx-target}
**Definition**: HTMX attribute specifying which element to update with the server's response. Uses CSS selector syntax.

**Example**: `hx-target="#task-list"` → response goes into element with `id="task-list"`.

**Default**: If not specified, targets the element that triggered the request.

**See also**: [hx-swap](#hx-swap), [HTMX](#htmx)

---

### hx-swap {#hx-swap}
**Definition**: HTMX attribute specifying **how** to insert the server's response into the target.

**Common values**:
- `innerHTML` (default) - Replace target's contents
- `outerHTML` - Replace target itself
- `beforeend` - Append to target (inside, at end)
- `afterend` - Insert after target (outside)
- `beforebegin` - Insert before target (outside)

**Example**: `hx-swap="beforeend"` → append new task to list.

**See also**: [hx-target](#hx-target), [HTMX](#htmx)

---

### hx-trigger {#hx-trigger}
**Definition**: HTMX attribute specifying what event triggers the AJAX request.

**Examples**:
- `hx-trigger="click"` (default for buttons)
- `hx-trigger="submit"` (default for forms)
- `hx-trigger="keyup changed delay:300ms"` (debounced search)
- `hx-trigger="revealed"` (lazy load when scrolled into view)

**See also**: [HTMX](#htmx)

---

### Template Factoring {#template-factoring}
**Definition**: Breaking templates into reusable partials (fragments) so the server can render full pages OR just fragments for HTMX.

**Example**:
- `base.peb` - Full page layout
- `tasks/index.peb` - Full page (extends base)
- `tasks/_list.peb` - Partial (just the `<ul>`)
- `tasks/_item.peb` - Partial (just one `<li>`)

**Why it matters**: Server returns `_list.peb` for HTMX requests, `index.peb` for full page loads.

**See also**: [Fragment](#fragment), [HTMX](#htmx)

---

## Web Technologies

### AJAX {#ajax}
**Full name**: Asynchronous JavaScript and XML

**Definition**: Technique for updating parts of a web page without a full reload. The browser sends a request in the background (via JavaScript), receives a response, and updates the DOM.

**History**: Name comes from early 2000s when XML was common. Modern AJAX typically uses JSON or HTML.

**Used in COMP2850**: HTMX uses AJAX under the hood, but you write HTML attributes instead of JavaScript `XMLHttpRequest` or `fetch()` code.

**See also**: [HTMX](#htmx)

---

### HTML {#html}
**Full name**: HyperText Markup Language

**Definition**: Standard markup language for web pages. Defines structure and semantics (headings, paragraphs, forms, links, etc.).

**Why it matters**: Semantic HTML (using correct tags like `<nav>`, `<main>`, `<button>`) is essential for accessibility.

**See also**: [Semantic HTML](#semantic-html)

---

### CSS {#css}
**Full name**: Cascading Style Sheets

**Definition**: Language for styling HTML (colors, fonts, layout, animations, etc.).

**Used in COMP2850**: Pico CSS framework for baseline styles, custom CSS for accessibility (skip links, focus indicators).

---

### JavaScript {#javascript}
**Definition**: Programming language that runs in the browser to add interactivity.

**Used in COMP2850**: Minimal JavaScript via HTMX library. All features must work without JavaScript (progressive enhancement).

**See also**: [Progressive Enhancement](#progressive-enhancement), [HTMX](#htmx)

---

### JSON {#json}
**Full name**: JavaScript Object Notation

**Definition**: Lightweight data format for exchanging data between server and client. Common in REST APIs.

**Used in COMP2850**: We **do not** use JSON APIs. Server sends HTML fragments, not JSON.

**Why**: Hypermedia approach. HTML is the data format.

**See also**: [Hypermedia](#hypermedia)

---

## Accessibility

### WCAG {#wcag}
**Full name**: Web Content Accessibility Guidelines

**Definition**: International standard for web accessibility published by W3C. Defines success criteria at 3 levels: A (minimum), AA (target), AAA (enhanced).

**Used in COMP2850**: We target **WCAG 2.2 Level AA** compliance for all features.

**Reference**: [W3C WCAG 2.2 Quick Reference](https://www.w3.org/WAI/WCAG22/quickref/)

**See also**: [Accessibility](#accessibility)

---

### ARIA {#aria}
**Full name**: Accessible Rich Internet Applications

**Definition**: Set of HTML attributes that provide semantic information to assistive technologies (screen readers, voice control). Includes roles (`role="alert"`), properties (`aria-label`), and states (`aria-expanded`).

**When to use**: Only when semantic HTML isn't sufficient. **First rule of ARIA**: Don't use ARIA if you can use semantic HTML instead.

**Used in COMP2850**: `role="status"`, `aria-live="polite"`, `aria-label` for context-specific buttons.

**Reference**: [WAI-ARIA Authoring Practices](https://www.w3.org/WAI/ARIA/apg/)

**See also**: [ARIA Live Region](#aria-live-region), [Semantic HTML](#semantic-html)

---

### ARIA Live Region {#aria-live-region}
**Definition**: Element that screen readers monitor for changes and announce automatically, without moving focus.

**Why it matters**: HTMX updates the page dynamically. Without live regions, screen reader users don't know anything changed.

**Attributes**:
- `role="status"` or `role="alert"` - What type of announcement
- `aria-live="polite"` - Wait for user to pause before announcing
- `aria-live="assertive"` - Interrupt immediately (errors only)

**Example**:
```html
<div id="status" role="status" aria-live="polite" class="visually-hidden">
  Task added successfully
</div>
```

**Used in COMP2850**: Every HTMX action must update a live region via OOB swap.

**See also**: [OOB Swap](#oob-swap), [Screen Reader](#screen-reader)

---

### Semantic HTML {#semantic-html}
**Definition**: Using HTML elements that convey **meaning** (semantics), not just structure or appearance.

**Examples**:
- ✅ Good: `<button type="submit">Add</button>`
- ❌ Bad: `<div onclick="submit()">Add</div>`
- ✅ Good: `<nav>`, `<main>`, `<article>`, `<section>`
- ❌ Bad: `<div class="nav">`, `<div class="main">`

**Why it matters**: Assistive technologies (screen readers, voice control) rely on semantic elements to understand page structure and functionality.

**See also**: [HTML](#html), [Accessibility](#accessibility)

---

### Screen Reader {#screen-reader}
**Definition**: Assistive technology that converts on-screen text and UI elements into synthesized speech or braille. Used by people who are blind or have low vision.

**Examples**:
- **NVDA** (Windows, free)
- **JAWS** (Windows, commercial)
- **VoiceOver** (macOS/iOS, built-in)
- **Orca** (Linux, built-in)

**Used in COMP2850**: All features must be tested with NVDA or VoiceOver to verify labels, announcements, and keyboard navigation.

**See also**: [ARIA Live Region](#aria-live-region), [Accessibility](#accessibility)

---

### Accessibility (a11y) {#accessibility}
**Definition**: Practice of designing products usable by people with disabilities (visual, auditory, motor, cognitive). "a11y" is numeronym (a + 11 letters + y).

**Core principles (POUR)**:
- **Perceivable** - Can see/hear content (alt text, captions, contrast)
- **Operable** - Can use keyboard/mouse/voice (keyboard nav, focus indicators)
- **Understandable** - Clear language, predictable behavior, error messages
- **Robust** - Works with assistive technologies (semantic HTML, ARIA)

**Used in COMP2850**: WCAG 2.2 AA compliance is mandatory from Week 6.

**See also**: [WCAG](#wcag), [ARIA](#aria), [Semantic HTML](#semantic-html)

---

### Skip Link {#skip-link}
**Definition**: Link at the top of a page that lets keyboard users jump directly to main content, bypassing repeated navigation.

**Why it matters**: WCAG 2.4.1 (Bypass Blocks). Users shouldn't have to tab through 50 nav links on every page.

**Implementation**:
```html
<a href="#main" class="skip-link">Skip to main content</a>
<!-- ... nav ... -->
<main id="main" tabindex="-1">Content here</main>
```

**Styling**: Hidden visually until keyboard-focused (`:focus`).

**See also**: [Accessibility](#accessibility), [WCAG](#wcag)

---

### Focus Indicator {#focus-indicator}
**Definition**: Visual outline or highlight that shows which element currently has keyboard focus.

**Why it matters**: WCAG 2.4.7 (Focus Visible). Keyboard users need to see where they are on the page.

**Requirements**: Minimum 3:1 contrast ratio against background, 2px minimum thickness (WCAG 2.2).

**Example**: Browser default blue outline, or custom CSS `outline: 3px solid #0066A1;`.

**See also**: [Accessibility](#accessibility), [WCAG](#wcag)

---

## HTTP & Server Concepts

### HTTP Methods {#http-methods}
**Definition**: Verbs that indicate the type of request being made to the server.

**Common methods**:
- **GET** - Retrieve data (safe, idempotent, cacheable)
- **POST** - Create new resource or submit data
- **PUT** - Update/replace entire resource
- **PATCH** - Partial update
- **DELETE** - Remove resource

**Used in COMP2850**: GET for viewing, POST for forms (add/edit/delete). HTMX supports all methods.

**See also**: [PRG](#prg)

---

### HTTP Status Codes {#http-status-codes}
**Definition**: 3-digit codes indicating the result of an HTTP request.

**Common codes**:
- **200 OK** - Success
- **201 Created** - Resource created successfully
- **303 See Other** - Redirect after POST (PRG pattern)
- **400 Bad Request** - Client error (validation failed)
- **404 Not Found** - Resource doesn't exist
- **500 Internal Server Error** - Server error

**Used in COMP2850**: 303 for PRG redirects, 201 for successful creation, 400 for validation errors.

**See also**: [PRG](#prg)

---

### Request Headers {#request-headers}
**Definition**: Key-value pairs sent by the browser with each HTTP request, providing metadata about the request.

**Examples**:
- `User-Agent: Mozilla/5.0...` (browser identity)
- `Accept: text/html` (what content types browser accepts)
- `HX-Request: true` (HTMX adds this to identify AJAX requests)
- `Content-Type: application/x-www-form-urlencoded` (form data format)

**Used in COMP2850**: Server checks `HX-Request` header to decide whether to return full page or fragment.

**See also**: [HTMX](#htmx), [Fragment](#fragment)

---

## HCI & UX Concepts

### Needs-Finding {#needs-finding}
**Definition**: Research method to understand what people actually need (not what they say they want). Uses observation, interviews, job stories, contextual inquiry.

**Why not "requirements gathering"**: Implies people know what they need and can articulate it. Needs-finding acknowledges we must discover unstated needs.

**Used in COMP2850**: Week 6 Lab 2 - conduct peer interviews, write job stories.

**See also**: [Job Stories](#job-stories), [People-Centred Language](#people-centred-language)

---

### Job Stories {#job-stories}
**Definition**: Format for capturing user needs based on context, motivation, and outcome. Structure: **"When [situation], I want [motivation], so I can [outcome], because [reasoning]."**

**Example**: "When I'm reviewing my weekly tasks on Sunday evening, I want to see incomplete items highlighted, so I can prioritize tomorrow's work, because I need to meet Friday's deadline."

**Why not user stories**: User stories ("As a user, I want...") focus on demographics. Job stories focus on context and causality.

**Used in COMP2850**: Week 6 Lab 2.

**See also**: [Needs-Finding](#needs-finding)

---

### People-Centred Language {#people-centred-language}
**Definition**: Putting the person first, not the disability or technology. Avoids deficit-based terms.

**Examples**:
- ✅ "Person using a screen reader" (not "blind user")
- ✅ "Keyboard user" (not "disabled person")
- ✅ "Person with low vision" (not "visually impaired user")

**Why it matters**: Disability arises from environmental barriers (bad design), not individual impairment. Language shapes how we think about accessibility.

**Used in COMP2850**: Module-wide terminology standard.

**See also**: [Accessibility](#accessibility)

---

### Heuristics {#heuristics}
**Definition**: General rules or principles for evaluating interface usability, not strict guidelines.

**Examples**:
- **Nielsen's 10 Usability Heuristics** (visibility of status, user control, consistency, error prevention, etc.)
- **Shneiderman's 8 Golden Rules** (consistency, shortcuts, feedback, closure, error handling, etc.)

**Used in COMP2850**: Week 7 accessibility audit uses heuristics + WCAG.

**See also**: [WCAG](#wcag), [Accessibility](#accessibility)

---

### Evaluation {#evaluation}
**Definition**: Process of measuring how well an interface meets user needs and usability goals.

**Types**:
- **Formative** - During design/development to guide iteration
- **Summative** - After release to measure success

**Metrics (COMP2850)**:
- **Utility** - Does it solve the problem?
- **Efficiency** - How quickly can people complete tasks?
- **Learnability** - How quickly can new users learn it?
- **Satisfaction** - How pleasant is it to use?
- **Affect** - Emotional response (confidence, frustration)

**Used in COMP2850**: Week 9 peer pilots, Week 10 analysis.

**See also**: [Task-Based Evaluation](#task-based-evaluation)

---

### Task-Based Evaluation {#task-based-evaluation}
**Definition**: Usability testing where participants attempt realistic tasks while you measure performance (time, errors, completion rate) and gather qualitative observations.

**Example**: "Add a task called 'Buy milk', mark it complete, then delete it" (measures core CRUD functionality).

**Used in COMP2850**: Week 9 Lab 2 - 4-person peer pilots with 4 tasks.

**See also**: [Evaluation](#evaluation)

---

## Module-Specific Terms

### Participant {#participant}
**Definition**: Person taking part in research (pilots, interviews, usability tests). Preferred term over "user" or "subject."

**Why**: More respectful, acknowledges agency, aligns with ethics protocols.

**Used in COMP2850**: Module-wide standard term.

**See also**: [People-Centred Language](#people-centred-language)

---

### PII (Personally Identifiable Information) {#pii}
**Full name**: Personally Identifiable Information

**Definition**: Data that can identify a specific individual (name, email, photo, student ID, IP address).

**Used in COMP2850**: We collect **no PII**. Only anonymous session IDs and timestamps. UK GDPR compliant.

**See also**: [Privacy by Design](#privacy-by-design)

---

### Privacy by Design {#privacy-by-design}
**Definition**: Approach to system design where privacy protections are built in from the start, not added later.

**Principles**:
1. Proactive (prevent, don't react)
2. Privacy as default setting
3. Privacy embedded in design
4. Full functionality (positive-sum, not zero-sum)
5. End-to-end security
6. Visibility and transparency
7. Respect for user privacy

**Used in COMP2850**: No PII collection, anonymous instrumentation, module-wide blanket consent.

**Reference**: `references/privacy-by-design.md`

**See also**: [PII](#pii)

---

### UK GDPR {#uk-gdpr}
**Full name**: UK General Data Protection Regulation (Data Protection Act 2018)

**Definition**: UK law governing personal data collection, storage, and processing.

**Key principles**: Lawfulness, fairness, transparency, purpose limitation, data minimization, accuracy, storage limitation, integrity/confidentiality, accountability.

**Used in COMP2850**: All instrumentation must be GDPR-compliant (no PII, verbal consent, opt-out supported).

**See also**: [Privacy by Design](#privacy-by-design), [PII](#pii)

---

## Frameworks & Tools

### Kotlin {#kotlin}
**Definition**: Modern programming language for JVM (Java Virtual Machine). Statically typed, null-safe, concise syntax.

**Used in COMP2850**: Server-side code (routes, data models, storage).

**See also**: [Ktor](#ktor)

---

### Ktor {#ktor}
**Definition**: Kotlin web framework for building server applications. Lightweight, asynchronous, uses coroutines.

**Used in COMP2850**: HTTP server, routing, template rendering (via Pebble plugin).

**Documentation**: [ktor.io](https://ktor.io/)

**See also**: [Kotlin](#kotlin), [Pebble](#pebble)

---

### Pebble {#pebble}
**Definition**: Template engine for Java/Kotlin. Similar to Jinja2 (Python) or Twig (PHP). Renders HTML with dynamic data.

**Syntax**:
- `{{ variable }}` - Output
- `{% for item in list %}...{% endfor %}` - Logic
- `{% extends "base.peb" %}` - Inheritance

**Used in COMP2850**: Server-side HTML rendering.

**Documentation**: [pebbletemplates.io](https://pebbletemplates.io/)

**See also**: [Ktor](#ktor), [Template Factoring](#template-factoring)

---

### Pico CSS {#pico-css}
**Definition**: Minimal CSS framework with semantic styling. Classless (styles semantic HTML tags) + optional utility classes.

**Why Pico**: Accessibility-first, no-JS, works with semantic HTML, lightweight (~10KB).

**Used in COMP2850**: Baseline styling for student web apps.

**Documentation**: [picocss.com](https://picocss.com/)

**See also**: [CSS](#css), [Semantic HTML](#semantic-html)

---

## Acronyms Quick Reference

| Acronym | Full Name | Link |
|---------|-----------|------|
| **a11y** | Accessibility (a + 11 letters + y) | [↑](#accessibility) |
| **AJAX** | Asynchronous JavaScript and XML | [↑](#ajax) |
| **ARIA** | Accessible Rich Internet Applications | [↑](#aria) |
| **CSS** | Cascading Style Sheets | [↑](#css) |
| **CSR** | Client-Side Rendering | [↑](#ssr) |
| **GDPR** | General Data Protection Regulation | [↑](#uk-gdpr) |
| **HATEOAS** | Hypermedia As The Engine Of Application State | [↑](#hateoas) |
| **HTML** | HyperText Markup Language | [↑](#html) |
| **HTTP** | HyperText Transfer Protocol | [↑](#http-methods) |
| **JSON** | JavaScript Object Notation | [↑](#json) |
| **OOB** | Out-of-Band (swap) | [↑](#oob-swap) |
| **PII** | Personally Identifiable Information | [↑](#pii) |
| **PRG** | Post-Redirect-Get | [↑](#prg) |
| **SSR** | Server-Side Rendering | [↑](#ssr) |
| **WCAG** | Web Content Accessibility Guidelines | [↑](#wcag) |

---

**Last updated**: 2025-10-15
**Module**: COMP2850 HCI, University of Leeds
