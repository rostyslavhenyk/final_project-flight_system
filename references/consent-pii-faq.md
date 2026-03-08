# Consent, PII, and Low-Risk Research FAQ (Weeks 6–7)

## Why this matters
Even with peer-to-peer studies in lab, we must follow university guidance and treat peers’ data respectfully. This FAQ clarifies what’s in scope for our blanket low-risk consent protocol.

---

## Quick definitions
- **PII (Personally Identifiable Information)**: Anything that can identify a person. In our labs it includes full names, student IDs, email addresses, recorded voices, facial images, device IDs.
- **De-identified notes**: Observations or timings that cannot point to a specific person (e.g., “Participant A took 48s to complete T2, mis-clicked once”).
- **Low-risk study**: Peer pairs, no external participants, no vulnerable groups, no sensitive topics, no recordings.

---

## What’s allowed in Week 6–7 labs?
| Activity | Allowed? | Notes |
|----------|----------|-------|
| Peer needs-finding interviews (with consent script) | ✅ | Keep to lab partners, no recording. Use initials or pseudonyms in notes. |
| Timing tasks during pilots (Stopwatch / server logs) | ✅ | Store in `data/metrics.csv` without names. |
| Collecting demographic data | ❌ | Out of scope; introduces unnecessary sensitivity. |
| Screenshots of peers | ❌ | Do not capture faces. Crop to interface only. |
| Recording audio/video | ❌ | Not covered by low-risk blanket approval. |

---

## Consent protocol essentials
1. **Introduce** the activity and remind peers participation is voluntary.
2. **Clarify** data collected (timings, errors, notes) and how it will be stored (local repo, private).
3. **Offer opt-out**: they can stop at any time, no penalty.
4. **Confirm no PII** is stored; use pseudonyms or IDs like `P1`, `P2`.
5. **Record consent** in `research/consent_protocol.md` (date, activity, initials if needed).

Sample script (use in Week 6 Lab 2):
> “We’re running a quick needs-finding chat about the task list app. I’ll take notes under `P1`, no names, and we won’t record. You can stop whenever you like. Okay to proceed?”

---

## Storing notes safely
- Keep notes in the repo under `research/` with pseudonyms.
- Do not sync to public forks. Push only to private module repos or upload via Minerva if required.
- If you accidentally capture PII, remove it immediately and note the correction in your reflection.

---

## Handling data after the lab
- Delete raw notes/screenshots containing identifying details once you’ve transcribed anonymised versions.
- For Gradescope submissions, ensure evidence folders contain cropped UI screenshots, no participant info.

---

## Common mistakes to avoid
- Writing “Spoke to Sam, she struggled with focus order” → instead use “Participant A…”
- Storing Google Form responses with email addresses → don’t collect emails (use plain Markdown tables).
- Sharing repo publicly before removing `research/` folder → keep private until evidence is sanitised.

---

## Who to ask if unsure?
- Lab teaching staff during sessions.
- Module leader via Minerva for edge cases (e.g., wanting to test with someone outside the cohort).

Document any unusual situations in your self-reflection so we can show due diligence.
