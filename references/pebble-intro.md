# Pebble templates in COMP2850

## What is Pebble?
Pebble is a lightweight HTML templating engine for the JVM. It takes plain text files (usually HTML) and replaces expressions, loops, and conditionals using the data you pass from Kotlin. Pebble renders on the server, so the browser receives fully formed HTML that works even when JavaScript is disabled.

## Why we use Pebble
- Server-first philosophy: we can build complete, accessible HTML before any enhancement.
- Safe by default: output is escaped unless you explicitly mark it as safe, which reduces XSS risks.
- Familiar syntax: Jinja- or Twig-style blocks (`{% %}`) and expressions (`{{ }}`) keep the learning curve gentle.
- Layouts and partials: `extends`, `block`, and `include` let us reuse structure and enforce consistency.
- No build tooling required: templates are plain files in `re./templates/` so they work on RHEL lab machines and Codespaces without extra setup.

## Mental model
1. Ktor gathers or builds the data (for example `tasks: List<Task>`).
2. We call `PebbleRender.render("tasks/index.peb", model)` to render HTML as a string.
3. Ktor sends that HTML to the browser. HTMX can then request fragments of the same templates.

Because rendering is server-side, keyboard-only usage, screen readers, and automated auditing tools get identical 
content to the HTMX-enhanced version.

## Syntax common examples
```pebble
{% extends "base.peb" %}
{% block content %}
  <h1>{{ title }}</h1>
  <ul>
    {% for task in tasks %}
      <li>{{ task.title }}</li>
    {% endfor %}
  </ul>
{% endblock %}
```

- `{% ... %}`: control structures (extends, block, if, for).
- `{{ ... }}`: output an expression; values are HTML-escaped automatically.
- `{# ... #}`: comments; they do not appear in the rendered output.

## Layouts and includes
- Define shared chrome in `base.peb` and expose replaceable sections with `{% block %}`.
- Pull reusable fragments into separate files and include them:
  ```pebble
  {% include "tasks/item.peb" with task=task %}
  ```
- Because includes are just files, we can create patterns like `_list.peb`, `_status.peb`, and `_pager.peb` once and reuse them across weeks.

## Passing data from Ktor
```kotlin
val model = mapOf(
    "title" to "Tasks",
    "tasks" to repo.all()
)
call.respondHtml(PebbleRender.render("tasks/index.peb", model))
```

Pebble sees the keys of the model map as variables in the template. Use descriptive names and prefer simple DTOs or immutable data to keep templates readable.

## Friendliness with HTMX and accessibility
- HTMX requests hit the same templates; we often render a partial (for example the `<li>` fragment) and return it.
- Live regions (`role="status"`) live in `base.peb`, so every page automatically announces status updates.
- Because Pebble renders semantic HTML, WCAG checks and screen readers work irrespective of JavaScript state.

## Debug tips
- Pebble line numbers appear in stack traces. If you see `Line 24, Column 10`, open that template and check the expression.
- When nothing renders, confirm the template path matches the file name and that you passed the expected keys in the model map.
- To inspect the final HTML, view source in the browser or log the rendered string locally.
