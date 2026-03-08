# Template Map — Week 8 Partials & Pagination

```
re./templates/
├── _layout/
│   └── base.peb         # Global HTML skeleton (skip link, live region, Pico.css)
├── tasks/
│   ├── index.peb        # Full page (extends base, renders form + task area)
│   ├── _list.peb        # `<ul>` wrapper + `aria-describedby`
│   ├── _item.peb        # Single `<li>` entry (title, buttons)
│   └── _pager.peb       # Pagination controls (Prev/Next links)
└── partials/
    └── (optional)       # Shared components like status indicators; not in starter pack
```

## Render paths
| Request | Template combination | Notes |
|---------|----------------------|-------|
| `GET /tasks` (full page) | `index.peb` → includes `_list` + `_pager` | Server-first baseline; no HTMX required |
| `GET /tasks/fragment` (HTMX) | `_list.peb` + `_pager.peb` + OOB status | Returns only the changing parts; live region updated via `hx-swap-oob` |
| `POST /tasks` (no JS) | Redirect back to `GET /tasks` | PRG keeps history clean |
| `POST /tasks` (HTMX) | `_item.peb` (+ status) appended to `#task-list` | `hx-target` on `<form>` handles swap |

## Data flow at a glance
1. **Repository** returns `Page<Task>` (items + paging metadata).
2. **Ktor route** builds model: `{ "title": "Tasks", "page": data, "q": query }`.
3. **Pebble** renders the templates using the model values.
4. **HTMX** swaps the fragment into `#task-area` or `#task-list`.

## Visual cheat sheet
```
index.peb
│
├── form (add task)         → hx-post /tasks (target: #task-list)
├── form (filter tasks)     → hx-get /tasks/fragment (target: #task-area)
└── <div id="task-area">
     ├── include _list.peb
     └── include _pager.peb
```

Remember: keep IDs stable (`task-{{ task.id }}`) so HTMX swaps the correct nodes, and reuse `_item.peb` everywhere to avoid diverging markup.
