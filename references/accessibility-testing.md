# Assistive Technology Testing Checklist (Week 6 → Week 11)

## What is Accessibility-First Design?

**Accessibility-first design** means building interfaces that work for everyone from the start—not retrofitting accessible features after the fact. In this module, we prioritise:

- **[WCAG](glossary.md#wcag) 2.2 AA compliance** as the minimum standard for all features
- **[Screen reader](glossary.md#screen-reader) compatibility** verified through NVDA/Orca testing in every lab
- **Keyboard navigation** ensuring all interactions work without a mouse
- **[No-JS parity](glossary.md#no-js-parity)** so core functionality remains available when JavaScript fails or is unavailable
- **Inclusive design** informed by people-centred language and real customer needs

Unlike "accessibility as an audit" (checking compliance at the end), accessibility-first means every design decision—from route structure to [ARIA](glossary.md#aria) attributes to colour contrast—is evaluated for inclusion before implementation. This approach reduces technical debt, improves usability for all users, and ensures legal compliance with the Equality Act 2010 and UK GDPR.

## Testing Checklist

Use this mini-check at the end of every lab to capture evidence quickly. Print it or keep it in your repo (`testing/checklist.md`).

| Area | Steps | Evidence to capture |
|------|-------|---------------------|
| Keyboard-only | Tab through the entire flow: skip link → forms → buttons. Ensure visible focus. | Screenshot or short note confirming order + any issues. |
| No-JS parity | Disable JS (DevTools) and repeat the task. Watch network panel to confirm only full-page requests. | Browser screenshot + note. If broken, log backlog item. |
| Screen reader (SR) | NVDA (Windows) or Orca (RHEL): navigate headings (`H`), forms (`F`), run the interaction, listen for live status. | Transcript snippet or notes of announcements. |
| Zoom & reflow | Zoom to 200%; ensure layout doesn’t break and no horizontal scroll on desktop widths. | Screenshot at 200% zoom. |
| Colour/contrast | Use built-in contrast checker or extension (e.g. Chrome DevTools → CSS overview). | Contrast report or note with values. |
| Error messaging | Trigger validation errors; confirm focus stays in context and SR announces the message. | Screenshot of error + note on announcement. |
| Metrics logging (when added) | Confirm `data/metrics.csv` records success + validation_error rows. | Copy of latest rows or summary in notes. |

## Tips
- Pair up: one person drives, another logs issues in `backlog/backlog.csv`.
- If a check fails, capture it immediately—auditors and Week 7-10 labs rely on real evidence.
- Create an `evidence/` directory in your repo and store artefacts per week (e.g., `evidence/wk6/`, `evidence/wk7/`) to keep things tidy.
