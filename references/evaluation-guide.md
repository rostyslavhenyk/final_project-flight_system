# Evaluation Metrics Quick Reference (Week 9–10)

## What are Evaluation Metrics?

**Evaluation metrics** are quantitative measures that help us assess the usability and effectiveness of interactive systems. Rather than relying on assumptions or opinions about whether an interface works well, metrics provide **evidence-based data** to identify problems, track improvements, and justify design decisions.

In HCI, we distinguish between:
- **Objective metrics** - Measurable performance data (time, error rates, completion rates) captured through instrumentation
- **Subjective metrics** - Self-reported experiences (confidence, satisfaction, perceived difficulty) captured through questionnaires

For COMP2850, we focus on **task-based evaluation**: participants attempt specific tasks (e.g., "filter tasks by status") while we measure completion time, success rates, and errors. This approach reveals usability issues that might not surface through inspection methods alone.

**Why metrics matter:**
- **Data-driven redesign** - Identify which tasks cause the most friction
- **Accessibility verification** - Compare JS-on vs JS-off performance to ensure [no-JS parity](glossary.md#no-js-parity)
- **Prioritisation** - Use error rates and completion times to rank backlog fixes by impact
- **Evidence chains** - Support claims in Task 1 and Task 2 submissions with concrete data, not guesswork

All metrics must respect [privacy by design](privacy-by-design.md) principles: we log anonymous session IDs and task codes, never personal identifiers.

---

## Why Task-Based Evaluation?

### Theoretical Foundation

**Task-based usability evaluation** has its roots in cognitive psychology and human factors research from the 1980s-90s. Rather than measuring abstract performance (e.g., reaction time, motor precision), task-based methods assess how well people can accomplish **realistic goals** with a system.

**Key foundations:**

- **ISO 9241-11 (2018)**: Defines usability as "the extent to which a system can be used by specified users to achieve specified goals with effectiveness, efficiency and satisfaction in a specified context of use"
- **Nielsen & Landauer (1993)**: Established the "5-user rule" - testing with 5 participants identifies ~85% of usability issues
- **Dumas & Redish (1993)**: *A Practical Guide to Usability Testing* - formalized task-based protocols for industry
- **Lewis (1982, 2014)**: Developed task-based metrics (completion rate, time-on-task, subjective ratings) still used today

**Why task-based over alternatives?**

| Method | What it measures | When to use | Why we don't use it here |
|--------|------------------|-------------|--------------------------|
| **Fitts' Law / ISO 9241-9** | Motor performance (pointing, clicking speed) | Low-level widget design (button size, target distance) | Too low-level; doesn't capture real workflow issues |
| **GOMS / KLM** | Expert performance (keystroke-level model) | Predict expert task time for routine operations | Assumes error-free performance; misses novice struggles |
| **Reaction time tests** | Perceptual-motor speed (stimulus → response) | Attention research, cognitive load studies | Doesn't reflect real task complexity |
| **Task-based evaluation** | Effectiveness, efficiency, satisfaction on realistic tasks | Formative evaluation, iterative design, accessibility testing | ✅ Matches our goal: find usability + accessibility issues in real workflows |

### Ecological Validity

Task-based evaluation prioritizes **ecological validity** - the extent to which findings generalize to real-world use. By asking participants to complete realistic scenarios ("add a task with a deadline"), we uncover issues that matter in practice:

- Form validation errors that block task completion
- Missing labels that confuse screen reader navigation
- Keyboard traps that prevent no-mouse workflows
- Performance differences between JS-on and JS-off conditions

These issues don't surface in abstract performance tests but critically affect real users.

### Our Approach in COMP2850

We use **lightweight task-based testing** inspired by:

- **Nielsen's discount usability engineering**: Small samples (n=4-5), qualitative + quantitative data, rapid iteration
- **Lewis's task-based metrics**: Completion rate, time-on-task, error rate, confidence ratings
- **WCAG evaluation methodology**: Test with assistive technology variants (keyboard, screen reader, no-JS)

**What we test:**
- Representative tasks from Week 6 needs-finding (job stories → evaluation tasks)
- Multiple interaction modes: mouse + keyboard, JS-on + JS-off, visual + screen reader
- Both **effectiveness** (Can people complete the task?) and **efficiency** (How quickly?)

**What makes our approach academically rigorous:**
- **Privacy-safe instrumentation**: Server-side logging (not surveillance)
- **Mixed methods**: Quantitative metrics + qualitative observations
- **Evidence chains**: Every claim traceable to data
- **Ethical protocols**: Informed consent, right to withdraw, no PII

For detailed task descriptions and assessment criteria, see [Task 1: Evaluation & Findings](../assessment/task1.md).

---

## Core Metrics

| Metric | How to calculate | Why we use it | Notes |
|--------|------------------|---------------|-------|
| Median time (`median_ms`) | Sort successful task durations; pick middle value (or average middle two) | Resistant to outliers; tells us typical completion time | Use Kotlin helper in `Analyse.kt` or spreadsheet `MEDIAN`. |
| Median absolute deviation (`mad_ms`) | Median of `|value - median|` for each duration | Measures spread without being skewed by extremes | Multiply by 1.4826 if you need an approx. standard deviation. |
| Completion rate | `success / (success + fail)` | Shows task feasibility; <1 means people are stuck | Track separately for JS-on vs JS-off. |
| Validation error count | Number of `validation_error` rows per task | Flags form issues (copy errors, missing labels) | Relates directly to accessibility backlog items. |
| Error rate | `validation_error / (success + validation_error)` | Highlights forms that confuse or block people | Pair with qualitative notes to prioritise fixes. |
| Confidence score | Average of 1–5 scale reported post-task | Taps into affective feedback (HCI evaluation requirement) | Capture in `metrics.csv` or a parallel sheet. |

## Workflow reminder
1. Append raw pilot data to `data/metrics.csv` (server logs + manual entries).
2. Run `./gradlew runAnalyse` (or `Analyse.kt`) to regenerate `analysis/analysis.csv`.
3. Copy summary rows into `analysis/summary.md` with narrative interpretation.
4. Use the numbers to populate `analysis/prioritisation.csv` (impact/inclusion/effort scores).

Keep this reference handy during Weeks 9–10 labs so you don’t have to re-derive the formulas under time pressure.
