# HTMX Pattern Cheat Sheet

Use these canonical patterns repeatedly across Weeks 6–10. Each snippet assumes the server exposes matching routes and keeps the no-JS fallback intact.

## 1. Active Search / Filter
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

## 2. OOB Status Messages
Announce changes without touching focus.

```html
<p id="status" role="status" aria-live="polite" class="visually-hidden"></p>
```

```kotlin
val status = """<div id="status" hx-swap-oob="true">Added "${task.title}".</div>"""
call.respondText(fragment + status, ContentType.Text.Html)
```

## 3. Inline Update (outerHTML swap)
Swap a container after a PATCH-like request.

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

## 8. Lazy load content

```html
<div hx-get="/tasks/details/{{ task.id }}"
     hx-trigger="revealed">
  Loading…
</div>
```

Keep parity: every pattern must have a server-rendered fallback so the same request works without HTMX attributes present.
