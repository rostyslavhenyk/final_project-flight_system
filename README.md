
# Glide Airways - The Reliable Flight System

## Generative AI acknowledgement

The overall page layout, project direction, and final testing/review were handled by the team, but generative AI was used to help us understand some parts of the code, generate spoof data, and help with some front-end design choices.

AI was used to explain how to debug merging/integrating the flight part of the code, and to check suggestions on how the data structure could be condensed into previously existing tables. The suggestions were followed, and the code has been reviewed since and proven to work.

The main AI-assisted areas were:

- `src/main/kotlin/data/flight/FlightConnectionBuilder.kt` - support with understanding and adapting the connecting-flight logic.
- `src/main/kotlin/data/flight/FlightScheduleGenerator.kt` and `src/main/kotlin/data/flight/FlightSeedData.kt` - spoof data for testing and demonstration.
- `src/main/kotlin/routes/flight/FlightBookingHelpers.kt` - initial query about how one would carry the data throughout pages, which was built on later.
- `src/main/resources/static/js/homepage.js` and `src/main/resources/static/js/flights-results.js` - used AI to explain the server-to-front-end data display, and some UI choices.
- `src/main/resources/static/js/homepage.js`, `src/main/resources/templates/user/homepage/index.peb`, and `src/main/resources/static/css/homepage.css` - used AI to solve front-end bugs and alignment issues that I do not know how to, helped me implement Flatpickr to display dates, and make each option to have a white background colour instead of the default black one. Also used AI to auto generate comments for each chunk to facilitate merging with other branches (only a few was kept in the end).
- `src/main/kotlin/routes/flight/SearchFlightsPageModels.kt` - inspired by AI to use queryParams to detect user input and use the data to generate choose flight pages
- `src/main/kotlin/routes/flight/BookSeatsPageModels.kt` - consulted AI to solve why offers are disappearing and added fallback preventions for JSON storage.
- Asked AI to scan for potential fallbacks of step 3 seats and extras, and it suggested when there are 9 passengers the names will overwhelm the URL, making data hard to find. It suggested a solution of combining all the names into 1 parameter instead of 9, and expressed it in base64url format to prevent data breaking of the names
- Asked AI to explain what kind of stuff should be covered by tests.
- Had to ask to highlight as to why the page was overloading `src/main/resources/templates/staff/chat/index.peb`, and then followed the advice for it to work fine.
Front-end behaviour and booking/search flows were manually tested in the running application.

# WCAG Accessibility Test Record

This table records a site-wide WCAG review of the Glide Airways flight booking system. The checks were carried out against the application templates, CSS and JavaScript, with practical keyboard-only behaviour assessed from the implemented markup and scripts. This is an internal accessibility test record suitable for project documentation; a formal certification would additionally require logged browser and assistive-technology test evidence.

| Check | Criterion | Level | Pass/Fail | Notes |
|---|---:|---:|---|---|
| K1 | 2.1.1 All actions keyboard accessible | A | Pass | Interactive actions are mostly implemented with keyboard accessibility in mind. |
| K2 | 2.4.7 Focus visible | AA | Pass | Global `:focus-visible` styling is present in `base.css`. Skip links also have visible focus styling. |
| K3 | No keyboard traps | A | Pass | No obvious keyboard trap was found in the code. Dialogs and lightboxes provide close/Escape behaviour. |
| K4 | Logical tab order | A | Pass | The page structure generally follows DOM order and uses native controls, so tab order is mostly logical. |
| K5 | Skip links present | AA | Pass | Both user and staff layouts include a “Skip to main content” link targeting `#main`. |
| F1 | 3.3.2 Labels present | A | Pass | Forms have visible or screen-reader labels, including the flight status route, flight number and date fields. |
| F2 | 3.3.1 Errors identified | A | Pass | Error/status messages are shown for forms and booking flows, with `role="alert"` or visible inline error regions in key workflows. |
| F3 | 4.1.2 Name, role, value | A | Pass | Controls expose names, roles and state. Custom dropdowns expose expanded/active state, staff chat open buttons expose `aria-expanded`, and dashboard toggle buttons expose `aria-pressed`. |
| D1 | 4.1.3 Status messages | AA | Pass | Global status regions, auth status messages, booking errors, contact/refund statuses and several result messages use `role="status"`, `role="alert"` or `aria-live`. |
| D2 | Live regions work | AA | Pass | Live chat uses `role="log"` and `aria-live`; unread chat badges and the dashboard unread count also expose polite live updates. |
| D3 | Focus management | A | Fail | Some flows move focus correctly, but modal/dialog focus handling is not fully consistent. |
| V1 | 1.4.3 Contrast minimum | AA | Pass | Main text and button colour combinations use strong foreground/background contrast. |
| V2 | 1.4.4 Resize text | AA | Pass | Layouts are mostly responsive and use scalable units/clamp patterns.|
| V3 | 1.4.11 Non-text contrast | AA | Pass | Most borders are green and stand out compared to the background. |
| S1 | 1.3.1 Headings hierarchy | A | Pass | Most pages have clear headings, fitting the criteria required. | 
| S2 | 2.4.1 Bypass blocks | A | Pass | Skip links are present in both main layouts and point to the main content region. |
| S3 | 1.1.1 Alt text | A | Pass | Image elements include `alt` text. Decorative images generally use empty alt text or are marked hidden. |

before running do this in powershell: Windows PowerShell:
$env:GLIDE_EMAIL_ADDRESS="glideairways.support@gmail.com"
$env:GLIDE_EMAIL_APP_PASSWORD="yekdqnxsmwedvlnz"
$env:GLIDE_STRIPE_PUBLISHABLE_KEY="pk_test_51TUNmpEBhmaPcmwS1yJuThZL4siddhg1vnp6CEUUu4ETr4NhTXoT7599H84nkO9RPbWQa0oKiS1m1K5SyZEYBRh0005D2GLTGT"
$env:GLIDE_STRIPE_SECRET_KEY="sk_test_51TUNmpEBhmaPcmwSu3W1VhKi7JKHpu8L9VkVC3bjHG3Ag1X7n0LjGlzfJXjkYLw0AH2RohKqJ9zdOO29qJmrC1Ou00Vgz9Bg3K"
Environment keys for email messages to work and stripe payment.
  
