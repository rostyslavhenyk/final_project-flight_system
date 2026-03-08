# Pebble Template Engine Cheatsheet

**Quick reference for COMP2850 HCI students**

---

## 1. Basic Syntax

### Three Types of Delimiters

| Delimiter | Purpose | Example |
|-----------|---------|---------|
| `{{ }}` | **Output** - Print expressions | `{{ task.title }}` |
| `{% %}` | **Tags/Logic** - Control flow | `{% if completed %}...{% endif %}` |
| `{# #}` | **Comments** - Not rendered | `{# TODO: Add pagination #}` |

---

## 2. Variables & Output

### Simple Variables
```pebble
{{ taskTitle }}           {# Outputs: "Buy groceries" #}
{{ taskCount }}           {# Outputs: 5 #}
{{ isCompleted }}         {# Outputs: true or false #}
```

### Object Properties (Dot Notation)
```pebble
{{ task.id }}             {# Accesses task object's id property #}
{{ task.title }}          {# Accesses task object's title property #}
{{ task.createdAt }}      {# Accesses task object's createdAt property #}
```

### Accessing Maps (from Kotlin)
```kotlin
// In Kotlin route:
mapOf("task" to task.toPebbleContext())

// In Pebble template:
{{ task.id }}             {# Accesses map key "id" #}
{{ task.completed }}      {# Accesses map key "completed" #}
```

---

## 3. Filters (Pipe Syntax)

Filters transform output values using the pipe `|` operator.

### Common Filters

| Filter | Purpose | Example | Output |
|--------|---------|---------|--------|
| `length` | Get collection size | `{{ tasks \| length }}` | `5` |
| `upper` | Uppercase | `{{ title \| upper }}` | `"BUY GROCERIES"` |
| `lower` | Lowercase | `{{ title \| lower }}` | `"buy groceries"` |
| `capitalize` | Capitalize first letter | `{{ status \| capitalize }}` | `"Complete"` |
| `escape` | HTML escape (auto in Pebble) | `{{ userInput \| escape }}` | `&lt;script&gt;` |
| `default` | Fallback if null/empty | `{{ title \| default("Untitled") }}` | `"Untitled"` if title is null |
| `date` | Format date | `{{ createdAt \| date("yyyy-MM-dd") }}` | `"2025-10-14"` |
| `trim` | Remove whitespace | `{{ title \| trim }}` | `"Buy groceries"` (no spaces) |

### Chaining Filters
```pebble
{{ task.title | lower | capitalize }}
{# "buy groceries" → "Buy groceries" #}

{{ tasks | length | default(0) }}
{# If tasks is null, output 0 #}
```

---

## 4. Control Flow

### If / Else
```pebble
{% if task.completed %}
  <span class="completed">✓ Done</span>
{% else %}
  <span class="pending">Pending</span>
{% endif %}
```

### If / ElseIf / Else
```pebble
{% if taskCount == 0 %}
  <p>No tasks yet.</p>
{% elseif taskCount == 1 %}
  <p>You have 1 task.</p>
{% else %}
  <p>You have {{ taskCount }} tasks.</p>
{% endif %}
```

### Comparison Operators
```pebble
{% if count > 5 %}          {# Greater than #}
{% if count >= 5 %}         {# Greater than or equal #}
{% if count < 5 %}          {# Less than #}
{% if count <= 5 %}         {# Less than or equal #}
{% if count == 5 %}         {# Equal (use ==, not =) #}
{% if count != 5 %}         {# Not equal #}
```

### Logical Operators
```pebble
{% if completed and visible %}           {# AND #}
{% if completed or visible %}            {# OR #}
{% if not completed %}                   {# NOT #}
{% if (a or b) and (c or d) %}          {# Grouping with () #}
```

### Checking for Null/Empty
```pebble
{% if tasks is null %}                   {# Is null #}
{% if tasks is not null %}               {# Is not null #}
{% if tasks is empty %}                  {# Is null or empty collection #}
{% if title is defined %}                {# Variable exists #}
```

---

## 5. Loops (For)

### Basic For Loop
```pebble
<ul>
  {% for task in tasks %}
    <li>{{ task.title }}</li>
  {% endfor %}
</ul>
```

### For Loop with Empty Fallback
```pebble
<ul>
  {% for task in tasks %}
    <li>{{ task.title }}</li>
  {% empty %}
    <li>No tasks to display.</li>
  {% endfor %}
</ul>
```

### Loop Variables (Special Properties)

Inside a `{% for %}` loop, you have access to special `loop` variables:

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `loop.index` | Current iteration (1-based) | `1, 2, 3, ...` |
| `loop.index0` | Current iteration (0-based) | `0, 1, 2, ...` |
| `loop.revindex` | Iterations remaining (1-based) | `5, 4, 3, ...` |
| `loop.revindex0` | Iterations remaining (0-based) | `4, 3, 2, ...` |
| `loop.first` | True if first iteration | `true` or `false` |
| `loop.last` | True if last iteration | `true` or `false` |
| `loop.length` | Total number of items | `5` |

**Example**:
```pebble
{% for task in tasks %}
  <li class="{% if loop.first %}first{% endif %} {% if loop.last %}last{% endif %}">
    {{ loop.index }}. {{ task.title }}
  </li>
{% endfor %}
```

Output:
```html
<li class="first">1. Buy groceries</li>
<li>2. Pay bills</li>
<li class="last">3. Call dentist</li>
```

---

## 6. Template Inheritance

### Base Template (`_layout/base.peb`)
```pebble
<!DOCTYPE html>
<html lang="en">
<head>
  <title>{% block title %}Default Title{% endblock %}</title>
</head>
<body>
  <header>
    <h1>Task Manager</h1>
  </header>

  <main>
    {% block content %}
      {# Default content if child doesn't override #}
    {% endblock %}
  </main>

  <footer>
    {% block footer %}
      <p>&copy; 2025 COMP2850</p>
    {% endblock %}
  </footer>
</body>
</html>
```

### Child Template (`tasks/index.peb`)
```pebble
{% extends "_layout/base.peb" %}

{% block title %}Task List{% endblock %}

{% block content %}
  <h2>Your Tasks</h2>
  <ul>
    {% for task in tasks %}
      <li>{{ task.title }}</li>
    {% endfor %}
  </ul>
{% endblock %}
```

**Result**: Child template inherits base layout, overrides `title` and `content` blocks.

---

## 7. Including Partials

### Include Without Parameters
```pebble
{% include "tasks/_item.peb" %}
```

### Include With Parameters
```pebble
{% for task in tasks %}
  {% include "tasks/_item.peb" with {"task": task} %}
{% endfor %}
```

### Include With Override (Pass Specific Variables)
```pebble
{% include "tasks/_list.peb" with {"tasks": completedTasks, "heading": "Completed Tasks"} %}
```

**Note**: `with` creates a new scope. Only variables explicitly passed are available in the included template.

---

## 8. COMP2850-Specific Patterns

### Dual-Mode HTMX Pattern
```pebble
{# Full page vs partial detection #}
{% if isHtmx %}
  {# Return fragment only (no layout) #}
  {% include "tasks/_list.peb" %}
{% else %}
  {# Return full page (extends base) #}
  {% extends "_layout/base.peb" %}
  {% block content %}
    {% include "tasks/_list.peb" %}
  {% endblock %}
{% endif %}
```

### ARIA Live Region (Status Announcements)
```pebble
<div id="status" role="status" aria-live="polite" aria-atomic="true">
  {% if statusMessage %}
    {{ statusMessage }}
  {% endif %}
</div>
```

### Out-of-Band (OOB) Updates
```pebble
{# Update status region separately from main content #}
<div id="status" hx-swap-oob="true" role="alert">
  Task "{{ task.title }}" added successfully!
</div>

{# Main content also returned in same response #}
<li id="task-{{ task.id }}">
  {{ task.title }}
</li>
```

### Task List with Accessibility
```pebble
<ul id="task-list" aria-describedby="task-count">
  {% for task in tasks %}
    <li id="task-{{ task.id }}">
      <span>{{ task.title }}</span>
      <form action="/tasks/{{ task.id }}/delete" method="post" style="display:inline;">
        <button type="submit" aria-label="Delete task: {{ task.title }}">Delete</button>
      </form>
    </li>
  {% empty %}
    <li>No tasks yet. Add one above!</li>
  {% endfor %}
</ul>
<p id="task-count" class="visually-hidden">
  Showing {{ tasks | length }} tasks.
</p>
```

---

## 9. Common Mistakes & Solutions

### ❌ Wrong: Using `=` for Comparison
```pebble
{% if count = 5 %}  {# WRONG: Use == #}
```

### ✅ Right: Use `==`
```pebble
{% if count == 5 %}  {# CORRECT #}
```

---

### ❌ Wrong: Missing Quotes in Strings
```pebble
{% if status == completed %}  {# WRONG: 'completed' is a variable #}
```

### ✅ Right: Quote String Literals
```pebble
{% if status == "completed" %}  {# CORRECT: String literal #}
```

---

### ❌ Wrong: Accessing Undefined Variables
```pebble
{{ user.name }}  {# ERROR if user is null #}
```

### ✅ Right: Use Default Filter
```pebble
{{ user.name | default("Guest") }}  {# Safe: Returns "Guest" if null #}
```

---

### ❌ Wrong: Forgetting `endfor` / `endif`
```pebble
{% for task in tasks %}
  <li>{{ task.title }}</li>
{# WRONG: Missing {% endfor %} #}
```

### ✅ Right: Always Close Tags
```pebble
{% for task in tasks %}
  <li>{{ task.title }}</li>
{% endfor %}  {# CORRECT #}
```

---

### ❌ Wrong: Using `{% %}` for Output
```pebble
{% task.title %}  {# WRONG: Use {{ }} for output #}
```

### ✅ Right: Use `{{ }}`
```pebble
{{ task.title }}  {# CORRECT #}
```

---

## 10. Debugging Tips

### Check Variable Type
```pebble
{# Temporarily output variable to see what it contains #}
<pre>{{ task }}</pre>
```

### Check If Variable Exists
```pebble
{% if task is defined %}
  Task exists: {{ task.title }}
{% else %}
  Task is undefined!
{% endif %}
```

### View Loop Variables
```pebble
{% for task in tasks %}
  <p>Index: {{ loop.index }}, First: {{ loop.first }}, Last: {{ loop.last }}</p>
{% endfor %}
```

### Escape HTML to See Raw Output
```pebble
<pre>{{ task.title | escape }}</pre>
```

---

## 11. Pebble vs Other Template Engines

If you've used other template engines, here's a quick comparison:

| Feature | Pebble | Thymeleaf | FreeMarker |
|---------|--------|-----------|------------|
| Variables | `{{ var }}` | `${var}` | `${var}` |
| If statement | `{% if %}` | `th:if` | `<#if>` |
| For loop | `{% for %}` | `th:each` | `<#list>` |
| Comments | `{# #}` | `<!--/* */-->` | `<#-- -->` |
| Inheritance | `{% extends %}` | `th:replace` | `<#include>` |

---

## 12. Resources

### Official Documentation
- **Pebble Docs**: https://pebbletemplates.io/
- **Syntax Guide**: https://pebbletemplates.io/wiki/guide/basic-usage/
- **Filters Reference**: https://pebbletemplates.io/wiki/filter/abs/

### COMP2850-Specific
- Week 6 Lab 1: Pebble syntax primer (inline)
- `pebble-intro.md`: Longer introduction to Pebble

### Getting Help
- **Syntax errors**: Check matching tags (`{% if %}` needs `{% endif %}`)
- **Variable undefined**: Use `| default()` filter or check spelling
- **Unexpected output**: Use `<pre>{{ var }}</pre>` to inspect variable

---

**Cheatsheet Version**: 1.0
**Last Updated**: 2025-10-14
**Module**: COMP2850 HCI
