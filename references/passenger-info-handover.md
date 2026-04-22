# Passenger info page handover — Glide Airways (`/book/passengers`)

This handover focuses on the passenger-details step only:
- template structure and where files now live,
- current client-side validation behaviour,
- what data is persisted vs not persisted yet,
- and recommended next backend work.

---

## 1) Page map and files

### Route and renderer
- Route: `GET /book/passengers`
- Kotlin handler: `handleBookPassengers` in `src/main/kotlin/routes/FlightsRoutes.kt`
- Template (folder-per-page): `src/main/resources/templates/flights/book-passengers/index.peb`

### Related assets
- Styles: `src/main/resources/static/css/flights-results.css`
- JS logic: `src/main/resources/static/js/flights-results.js`

---

## 2) UI structure (what users see)

1. Booking stepper (Step 2 active)
2. Hero heading (`Passenger details`)
3. Guest sign-in strip (only when not logged in)
4. Passenger cards (Title + First name + Surname per passenger)
5. Contact card (Country code, Phone number, Email, Membership Number)
6. Continue CTA to `/book/seats`

The page currently validates required fields inline and blocks Continue until the first failing field is corrected.

---

## 3) Data source and model wiring

`handleBookPassengers` builds the view model from query/session:
- Query (booking context): `from`, `to`, `depart`, `return`, `trip`, `fare`, `flight`, `price`, passenger counts, leg context (`leg`, `ob*`).
- Derived links: `backToChooseFlightsHref`, `continueSeatsHref`, `loginHref`.
- Passenger rows: produced by `buildPassengerRowModels(adults, children)`.
- Auth/session: if logged in, `membershipValue` is derived from session id.

Template behaviour:
- Logged out: membership field is readonly and prefilled `N/A`.
- Logged in: membership field is prefilled with `membershipValue`.

---

## 4) Validation behaviour (current implementation)

Validation is client-side in `flights-results.js`:
- Name sanitization: letters + spaces/hyphen/apostrophe only.
- Country code and phone: digits only.
- Per-passenger checks in order: title, first name, surname.
- Contact checks in order: country code, phone, email blank, email must contain `@`.
- Only the first failing rule is shown at a time.
- Error message appears in a red box directly under the related field/card.

These checks run on Continue click and on form submit interception.

---

## 5) Persistence status (critical)

### Persisted now
- Flight/fare selection context is persisted in query string between steps.
- Login state/session id is persisted via server session.

### Not persisted yet
- Passenger/contact form values are not saved server-side yet.
- Current form is still a demo scaffold for validation/UI:
  - no backend save endpoint,
  - Continue is still navigation to `/book/seats`,
  - values are not written to a booking draft model.

---

## 6) Unfinished parsing/integration checklist

1. Define a booking-draft payload model for passenger/contact data.
2. Add POST endpoint to validate and save the payload.
3. Mirror JS validation rules in Kotlin server validation.
4. Bind server-side errors back into template model for SSR display.
5. Persist passenger/contact values into next steps and final confirmation.
6. Decide storage target for in-progress booking (session-only vs repository/database).

---

## 7) Notes for maintainers

- Keep this file in sync when:
  - changing field ids/classes used by `flights-results.js`,
  - changing passenger/contact validation text or order,
  - adding real persistence on `/book/passengers`.
