# Assistive Technology Testing Checklist (Week 6 → Week 11)

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
