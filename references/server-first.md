# Server‑First: A Practical Guide for COMP2850 (HCI)

> **TL;DR**: Render HTML on the **server** by default. Add interactivity as **[progressive enhancement](glossary.md#progressive-enhancement)** using [HTMX](glossary.md#htmx). If JavaScript is off, everything still works. Use **[PRG (Post/Redirect/Get)](glossary.md#prg)** for forms, return **[fragments](glossary.md#fragment)** to HTMX when it asks, and make **[accessibility](glossary.md#accessibility)** non‑negotiable.

**New to these terms?** See the **[Glossary](glossary.md)** for full definitions.

---

## Why "server‑first"?
- **Reliability**: No dependency on client frameworks to show a page or submit a form.
- **Simplicity**: Routing, validation, and business logic live on the server ([Ktor](glossary.md#ktor)). You avoid duplicating logic in the browser.
- **Performance**: Initial loads are fast (HTML streams quickly); interactivity is layered as needed.
- **[Accessibility](glossary.md#accessibility)**: [Semantic HTML](glossary.md#semantic-html), sensible focus order, and [ARIA](glossary.md#aria) work from the start.

---

## Core principles
1. **Server renders the UI** (full pages + partials).
2. **[Progressive enhancement](glossary.md#progressive-enhancement)** with [HTMX](glossary.md#htmx) (or plain forms and links).
3. **[No‑JS parity](glossary.md#no-js-parity)** is mandatory—every action has a full-page path.
4. **[PRG pattern](glossary.md#prg)** for all forms (avoid resubmits; clean URLs).
5. **One source of truth**: validation and business rules on the server.
6. **[Accessibility](glossary.md#accessibility) by default**: structure, labels, keyboard paths, and [live updates](glossary.md#aria-live-region).

---

## Quick‑start checklist
- [ ] **Routes** return full pages by default; fragments when `HX-Request: true` is present.  
- [ ] **Forms** use PRG: `POST /thing` → validate → save → `redirect("/things")`.  
- [ ] **HTMX** requests hit the *same* routes; server returns only the fragment (no layout).
- [ ] **Validation** errors re-render the form (full page or fragment) with error messages bound to fields.  
- [ ] **Announcements** use `aria-live="polite"` and/or `hx-swap-oob` for status banners.  
- [ ] **No client-only state machines**. Server owns state.  
- [ ] **Links still work** with normal navigation (use `hx-boost="true"` as a [sprinkle](https://hypermedia.systems/htmx-patterns/) only).  
- [ ] **Keyboard & screen reader** flows are tested (Tab order, headings, labels).  
- [ ] **JS disabled** tests pass (you can complete tasks end-to-end).

---

## Minimal reference implementation

### 1) Routing in Ktor (Kotlin)
Use a helper to detect HTMX and return the right view.

```kotlin
fun Application.module() {
    routing {
        // List
        get("/tasks") {
            val tasks = taskRepo.all()
            if (call.request.headers["HX-Request"] == "true") {
                call.respond(renderPartial("_tasks_table.peb", mapOf("tasks" to tasks)))
            } else {
                call.respond(renderPage("tasks.peb", mapOf("tasks" to tasks)))
            }
        }

        // Create (PRG)
        post("/tasks") {
            val params = call.receiveParameters()
            val title = params["title"]?.trim().orEmpty()

            val errors = mutableMapOf<String, String>()
            if (title.isBlank()) errors["title"] = "Title is required."

            if (errors.isNotEmpty()) {
                val model = mapOf("errors" to errors, "values" to params)
                if (call.request.headers["HX-Request"] == "true") {
                    // return just the form fragment with errors
                    call.respond(HttpStatusCode.UnprocessableEntity, renderPartial("_task_form.peb", model))
                } else {
                    call.respond(renderPage("task_new.peb", model))
                }
                return@post
            }

            taskRepo.add(title)
            // PRG: after success, redirect for full-page; or return fragment for HTMX
            if (call.request.headers["HX-Request"] == "true") {
                val tasks = taskRepo.all()
                call.respond(renderPartial("_tasks_table.peb", mapOf("tasks" to tasks, "flash" to "Task added")))
            } else {
                call.respondRedirect("/tasks?flash=Task+added")
            }
        }
    }
}
```

> **Notes**  
> - `renderPage(template, model)` returns the **full** layout (header/footer + body).  
> - `renderPartial(template, model)` returns a **fragment** only (no Chrome).  
> - HTMX sets `HX-Request: true` automatically—use it to branch responses.  
> - On error, send **422 Unprocessable Entity** for HTMX (helps debugging).

### 2) Templates (Pebble/FreeMarker/etc.)

**`tasks.peb`** (full page)
```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>Tasks</title>
</head>
<body>
  <main>
    <h1>Tasks</h1>

    <div id="alerts" aria-live="polite">
      {% if flash %}<div class="alert">{{ flash }}</div>{% endif %}
    </div>

    <section>
      {% include "_task_form.peb" %}
    </section>

    <section id="tasks-table">
      {% include "_tasks_table.peb" %}
    </section>
  </main>
</body>
</html>
```

**`_task_form.peb`** (fragment)
```html
<form action="/tasks" method="post"
      hx-post="/tasks" hx-target="#tasks-table" hx-swap="innerHTML">
  <label for="title">Title</label>
  <input id="title" name="title" value="{{ values.title | default('') }}"
         aria-invalid="{{ errors.title ? 'true' : 'false' }}"
         aria-describedby="{{ errors.title ? 'title-error' : '' }}">

  {% if errors.title %}
    <div id="title-error" class="error">{{ errors.title }}</div>
  {% endif %}

  <button type="submit">Add</button>
</form>
```

**`_tasks_table.peb`** (fragment)
```html
<table role="table">
  <thead>
    <tr><th>Title</th><th>Actions</th></tr>
  </thead>
  <tbody>
  {% for t in tasks %}
    <tr>
      <td>{{ t.title }}</td>
      <td>
        <button hx-delete="/tasks/{{ t.id }}"
                hx-target="#tasks-table"
                hx-swap="innerHTML">Delete</button>
      </td>
    </tr>
  {% endfor %}
  </tbody>
</table>

<!-- Optional OOB flash update -->
<div id="alerts" hx-swap-oob="true" aria-live="polite">
  {% if flash %}<div class="alert">{{ flash }}</div>{% endif %}
</div>
```

> **Why this works**  
> - Full page render for normal navigation.  
> - HTMX swaps only `#tasks-table` (fast, minimal HTML over the wire).  
> - Same URLs & same controllers for both full and partial flows.  
> - `hx-swap-oob` lets us update the flash area *outside* the target region.

---

## PRG (Post/Redirect/Get) done right
- **Full page**: `POST /tasks` → validate → save → `302 Location: /tasks` → browser GETs → shows “Task added”.  
- **HTMX**: `POST /tasks` → validate → save → return **updated fragment** (or send header `HX-Redirect: /tasks` if you want HTMX to follow a redirect).  
- **Avoid** returning “success pages” to a POST. They break refresh/back and risk duplicate submissions.

---

## Validation & errors
- Use server-side validators; **never** rely solely on client hints.  
- Return **422** with the form fragment and inline errors for HTMX.  
- For full pages, re-render the page with the form and errors bound.  
- Keep focus management in mind—on error, focus the first invalid field.

---

## Accessibility essentials (baked in)
- Semantic HTML: headings, lists, labels, landmark regions (`<main>`, `<nav>`).  
- **Labels** bound to inputs; **error text** bound with `aria-describedby`.  
- Buttons are real `<button>` elements; links are `<a>` elements (not divs).  
- **Focus states** visible; Tab order logical.  
- Live updates (`aria-live="polite"`) for flash/status messages.  
- Test with keyboard only and with a screen reader (e.g., Orca/NVDA/VoiceOver).

---

## Progressive enhancement patterns

### A) Boost links & forms (optional)
```html
<body hx-boost="true">
  <!-- Links and forms become HTMX requests automatically.
       Turn off if it confuses the flow for beginners. -->
</body>
```

### B) Keep URLs tidy on partial swaps
If a swap represents a “real” navigation, add `hx-push-url="true"` so the back button works as expected.

### C) Small, composable fragments
Prefer `_task_row.peb` included by `_tasks_table.peb` so you can swap a single row on update, e.g. `hx-target="#task-{{id}}"`.

---

## Debugging & testing
- **No‑JS test**: Disable JS and complete every critical flow (create, list, delete).  
- **HTMX visibility**: In the browser console, run `htmx.logAll()` to see events.  
- **Headers**: Confirm `HX-Request: true` in devtools for HTMX requests.  
- **Status codes**: Use 200/302 for success; 422 for validation errors.  
- **cURL**: Simulate HTMX:
  ```bash
  curl -H "HX-Request: true" http://localhost:8080/tasks
  ```

---

## Security notes
- **CSRF**: Include a CSRF token in all forms (double-submit cookie or server session token). HTMX will submit it like any other field.  
- **Method safety**: Use proper HTTP verbs (GET is read‑only; POST/PUT/PATCH/DELETE mutate).  
- **Validation & encoding**: Validate inputs; encode all dynamic output in templates.

---

## Performance tips
- Keep fragments **small** and **cacheable** where appropriate.  
- Avoid sending large JSON blobs. Send just the HTML you need.  
- Consider **ETags/Last‑Modified** for GET endpoints with expensive renders.

---

## Common anti‑patterns (avoid)
- ❌ Two separate apps (SPA + API) for simple CRUD—overkill here.  
- ❌ Client‑side validation only—excludes customers and risks bad data.  
- ❌ Hidden, fragile UI state that the server doesn’t know about.  
- ❌ POST responses that render success pages without redirects (breaks back/refresh).

---

## “Make it real” checklist for your lab
1. Build the **list** + **create** flows using the patterns above.  
2. Add **inline validation** and a **flash** region.  
3. Prove **no‑JS parity** by completing the flow with JS turned off.  
4. Add one **OOB update** (e.g., flash, count badge).  
5. Write a **one‑page test plan**: steps, expected results, and screenshots.

---

## Appendix: tiny helper (Ktor)
```kotlin
fun ApplicationCall.isHtmx() = request.headers["HX-Request"] == "true"
```
Use `if (call.isHtmx()) renderPartial(...) else renderPage(...)` to keep controllers tidy.

---

### Final thought
**Server‑first ≠ anti‑JavaScript.** It's about choosing HTML as the reliable baseline, then layering interaction where it genuinely improves the experience. You'll build features faster, with fewer bugs, and everyone—including people navigating with assistive tech—benefits.
