# Worked Example — Accessible Inline Edit (Week 7 Lab 2)

## Scenario
Inline edit form for updating a task title. 
Original implementation failed accessibility checks:
- No label tied to the input generated for editing
- Error message appended visually but not announced
- Focus jumped unexpectedly after save

## Before (problematic code)
```pebble
<li id="task-{{ task.id }}">
  <span>{{ task.title }}</span>
  <form action="/tasks/{{ task.id }}/edit" method="post"
        hx-post="/tasks/{{ task.id }}/edit"
        hx-target="#task-{{ task.id }}">
    <input name="title" value="{{ task.title }}">
    <button type="submit">Save</button>
  </form>
</li>
```

Issues found in audit:
- Missing `<label>` and `id` → fails WCAG 1.3.1 (A)
- No error summary or field-level association
- Screen readers received no confirmation message

## After (fixed version)
```pebble
<li id="task-{{ task.id }}">
  <form action="/tasks/{{ task.id }}/edit" method="post"
        hx-post="/tasks/{{ task.id }}/edit"
        hx-target="#task-{{ task.id }}"
        hx-swap="outerHTML">
    <label class="visually-hidden" for="title-{{ task.id }}">
      Edit title for {{ task.title }}
    </label>
    <input id="title-{{ task.id }}" name="title" value="{{ task.title }}"
           aria-describedby="hint-{{ task.id }}">
    <small id="hint-{{ task.id }}" class="visually-hidden">
      Keep titles concise; changes announce in status area.
    </small>
    <button type="submit">Save</button>
  </form>
</li>
```

Server route (Ktor) now logs validation errors and returns OOB status updates:
```kotlin
post("/tasks/{id}/edit") {
    val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
    val title = call.receiveParameters()["title"].orEmpty().trim()
    if (title.isBlank()) {
        val fragment = PebbleRender.render("tasks/edit-error.peb", mapOf("task" to repo.get(id)))
        val status = """<div id="status" hx-swap-oob="true">Title is required.</div>"""
        // Logger implementation covered in Week 9; shown here for completeness
        Logger.write(session = sid(call), req = reqId(call), task = "T2_edit", step = "validation_error", outcome = "blank_title", ms = 0, status = 400, js = jsMode(call))
        return@post call.respondText(fragment + status, ContentType.Text.Html)
    }

    repo.update(id, title)
    val fragment = PebbleRender.render("tasks/item.peb", mapOf("task" to repo.get(id)))
    val status = """<div id="status" hx-swap-oob="true">Updated "$title".</div>"""
    call.respondText(fragment + status, ContentType.Text.Html)
}
```

Checklist we ticked off:
- ✅ Field has a label (visually hidden) and `aria-describedby`
- ✅ Error path returns fragment with inline message + status update
- ✅ Success path announces change via live region (`status` element in base template)
- ✅ Focus management: HTMX attempts to restore focus to the matching `id="title-{{ task.id }}"` after swap; test with keyboard navigation to verify behaviour
- ✅ Logger records validation errors for metrics analysis (Week 9 addition)

## Evidence to capture (for Task 1)
- Screenshot of before/after (with annotations on label / status)
- Screen reader transcript (NVDA) confirming: “Updated "Submit report".”
- Backlog entry referencing WCAG 1.3.1 and 4.1.3

Use this pattern as a blueprint: the key is tying markup + server response + evidence together.
