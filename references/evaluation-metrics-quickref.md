# Evaluation Metrics Quick Reference (Week 9–10)

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
