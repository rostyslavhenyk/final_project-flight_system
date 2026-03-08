# HTMX Patterns & Progressive Enhancement

## What is HTMX?

**HTMX** is a JavaScript library that lets you build **dynamic web interfaces** using **HTML attributes** instead of writing JavaScript code. It extends HTML with attributes like `hx-get`, `hx-post`, `hx-target`, and `hx-swap` that trigger [AJAX](glossary.md#ajax) requests and update parts of the page.

**Key idea**: The server sends **[HTML fragments](glossary.md#fragment)** (not [JSON](glossary.md#json)), and HTMX swaps them into the page. You write normal HTML forms and links, add a few `hx-*` attributes, and suddenly they work dynamically without page reloads.

**Core text**: [Hypermedia Systems](https://hypermedia.systems/) by Carson Gross, Adam Stepinski, and Deniz Akşimşek (2023) - read Chapters 1-6 for foundations.

## Why HTMX for HCI?

1. **[Accessibility](glossary.md#accessibility) by default** - Server controls HTML structure, ensuring [semantic markup](glossary.md#semantic-html), [ARIA](glossary.md#aria) roles, and [screen reader](glossary.md#screen-reader) compatibility
2. **[Progressive enhancement](glossary.md#progressive-enhancement)** - Everything works without JavaScript; HTMX enhances the experience when available
3. **Simplicity** - No build tools, no client-side state management, no framework complexity
4. **[Hypermedia](glossary.md#hypermedia)-driven** - Follows [REST](glossary.md#rest)/[HATEOAS](glossary.md#hateoas) principles - the server tells the client what to display (HTML) and what actions are available (links/forms)

## How it works

1. **Human interaction** - Click a button, submit a form, type in a search box
2. **HTMX sends AJAX request** - Adds `HX-Request: true` [header](glossary.md#request-headers) so server knows it's AJAX
3. **Server responds with HTML** - Returns a [fragment](glossary.md#fragment) (e.g., `<li>New item</li>`) not a full page
4. **HTMX swaps the fragment** - Updates the target element (append, replace, etc.)
5. **Screen readers announce** - [ARIA live regions](glossary.md#aria-live-region) announce changes automatically

**Example**:
```html
<button hx-get="/tasks/123" hx-target="#content">Load Task</button>
```
- Click → AJAX GET to `/tasks/123`
- Server returns HTML: `<div>Task details...</div>`
- HTMX replaces `#content` with the response
- No page reload, no JavaScript written by you

## Core Attributes

| Attribute | Purpose | Example |
|-----------|---------|---------|
| `hx-get` | HTTP GET request | `<button hx-get="/tasks">` |
| `hx-post` | HTTP POST request | `<form hx-post="/tasks">` |
| `hx-target` | Where to insert response | `hx-target="#task-list"` |
| `hx-swap` | How to insert (replace/append/etc.) | `hx-swap="beforeend"` |
| `hx-trigger` | What event triggers request | `hx-trigger="keyup changed delay:300ms"` |
| `hx-swap-oob` | Update element outside target | `<div id="status" hx-swap-oob="true">` |

## Progressive Enhancement Pattern

Every HTMX feature must have a **[no-JS fallback](glossary.md#no-js-parity)**:

```html
<!-- Works WITHOUT JavaScript (full page POST-Redirect-GET) -->
<!-- Works WITH JavaScript (HTMX AJAX, fragment swap) -->
<form action="/tasks" method="post"
      hx-post="/tasks"
      hx-target="#task-list"
      hx-swap="beforeend">
  <input name="title" required>
  <button type="submit">Add Task</button>
</form>
```

**Server detects HTMX**:
```kotlin
if (call.request.headers["HX-Request"] == "true") {
    // Return fragment for HTMX
    call.respondText("<li>New task</li>", ContentType.Text.Html)
} else {
    // Return full page or redirect for no-JS
    call.respondRedirect("/tasks")
}
```

---

## Common Patterns

Use these canonical patterns repeatedly across Weeks 6–10. Each snippet assumes the server exposes matching routes and keeps the no-JS fallback intact.

## 1. Active Search / Filter
**📖 Reference**: [Hypermedia Systems, Ch. 6: More HTMX Patterns](https://hypermedia.systems/more-htmx-patterns/#_active_search)

Filter results as people type, with history support and a live status update.

```html
<form action="/tasks" method="get"
      hx-get="/tasks/fragment"
      hx-target="#task-area"
      hx-trigger="keyup changed delay:300ms, submit from:closest(form)"
      hx-push-url="true">
  <label for="q">Filter tasks</label>
  <input id="q" name="q" type="search" aria-describedby="q-hint">
  <small id="q-hint">Type to filter. Works without JavaScript.</small>
  <button type="submit">Apply</button>
</form>

<div id="task-area" hx-indicator="#loading">
  <progress id="loading" class="visually-hidden" aria-hidden="true"></progress>
  {% include "tasks/_list.peb" %}
  {% include "tasks/_pager.peb" %}
</div>
```

Server path (return list + pager + status when HX request):
```kotlin
get("/tasks/fragment") {
    val q = call.request.queryParameters["q"].orEmpty()
    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
    val data = repo.search(q, page)
    val list = PebbleRender.render("tasks/_list.peb", mapOf("page" to data, "q" to q))
    val pager = PebbleRender.render("tasks/_pager.peb", mapOf("page" to data, "q" to q))
    val status = """<div id="status" hx-swap-oob="true">Found ${data.total} tasks.</div>"""
    call.respondText(list + pager + status, ContentType.Text.Html)
}
```

## 2. OOB (Out-of-Band) Status Messages
**📖 Reference**: [HTMX Docs: hx-swap-oob](https://htmx.org/attributes/hx-swap-oob/) | [Hypermedia Systems, Ch. 9](https://hypermedia.systems/hypermedia-on-the-web/#_practical_patterns)

Announce changes without touching focus.

```html
<p id="status" role="status" aria-live="polite" class="visually-hidden"></p>
```

```kotlin
val status = """<div id="status" hx-swap-oob="true">Added "${task.title}".</div>"""
call.respondText(fragment + status, ContentType.Text.Html)
```

## 3. Inline Edit (Click to Edit)
**📖 Reference**: [Hypermedia Systems, Ch. 5: HTMX Patterns](https://hypermedia.systems/htmx-patterns/#_click_to_edit)

Swap a container after a PATCH-like request (inline editing).

```html
<form action="/tasks/{{ task.id }}/edit" method="post"
      hx-post="/tasks/{{ task.id }}/edit"
      hx-target="#task-{{ task.id }}"
      hx-swap="outerHTML">
  <!-- label + input + button -->
</form>
```

## 4. Deferred Swap (after swap delay)
Useful for optimistic UI (e.g. show success, then clear form).

```html
<div hx-target="this"
     hx-swap="outerHTML settle:1s">
  <p class="success">Saved!</p>
</div>
```

## 5. Multi-target updates
Use `hx-swap-oob` to update multiple DOM nodes from one response.

```html
<div id="summary" hx-swap-oob="true">…</div>
<li id="task-3">…</li>
```

## 6. Indicators & Disabled States

```html
<form hx-post="/tasks" hx-target="#task-list" hx-disabled-elt="[data-disable]">
  <button data-disable>Save</button>
  <div class="spinner" hx-indicator></div>
</form>
```

## 7. Confirm/Cancel Actions

```html
<button hx-delete="/tasks/{{ task.id }}"
        hx-target="#task-{{ task.id }}"
        hx-swap="outerHTML"
        hx-confirm="Delete this task?">
  Delete
</button>
```

## 8. Lazy Loading
**📖 Reference**: [Hypermedia Systems, Ch. 6: More HTMX Patterns](https://hypermedia.systems/more-htmx-patterns/#_lazy_loading)

Lazy load content

```html
<div hx-get="/tasks/details/{{ task.id }}"
     hx-trigger="revealed">
  Loading…
</div>
```

Keep parity: every pattern must have a server-rendered fallback so the same request works without HTMX attributes present.
