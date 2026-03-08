```mermaid
graph TD
  Base[_layout/base.peb]
(skip link, live region, Pico.css)
  Base --> Index[tasks/index.peb]
  Index --> List[tasks/_list.peb]
  List --> Item[tasks/_item.peb]
  Index --> Pager[tasks/_pager.peb]
  Item -->|Forms| Routes[(Ktor routes)]
  Pager -->|Links| Routes
  Routes --> HTMX[HTMX fragment responses]
  Routes --> PRG[Full-page PRG responses]
```