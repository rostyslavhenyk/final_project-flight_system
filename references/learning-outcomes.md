# Learning Outcomes Reference

> **Purpose** This document is the **definitive reference** for all Learning Outcomes in COMP2850 HCI. It clarifies terminology, maps outcomes to weeks/labs, and ensures consistency across all teaching materials.

---

## Understanding the Hierarchy

There are **three levels** of learning specifications in this module:

| Level | Scope | Purpose | Where Used |
|-------|-------|---------|------------|
| **Module Aims** | Broad, aspirational statements about overall module goals | High-level intentions; what the module sets out to achieve | Homepage, module outline |
| **Learning Outcomes (LOs)** | Specific, measurable competencies students will demonstrate | Assessment criteria; aligned to accreditation standards (ACM CS2023) | Week/lab mappings, assessment rubrics, portfolio |
| **Lab Objectives** | Session-specific tasks and activities | What students will do in this lab to work toward LOs | Individual lab pages |

**Key distinction**:
- **Aims** are broad intentions ("enable students to...")
- **Outcomes** are measurable achievements ("students will be able to...")
- **Objectives** are specific activities ("implement a consent form", "run 4 pilots")

---

## Module-Wide Learning Outcomes (COMP2850)

These **10 outcomes** apply to the **entire COMP2850 module** (Weeks 1-11, covering OOP + HCI). The HCI component (Weeks 6-11) contributes to these outcomes alongside the OOP component (Weeks 1-5).

On successful completion of COMP2850, students will have demonstrated the ability to:

1. **Apply subject specific knowledge and engineering design principles** to design and implement software artefacts which satisfy complex real-world requirements, considering accessibility and inclusive design principles *(C1, M1, C2, M2, C5, M5, C6, M6, C11, M11)*

2. **Select and interpret sources of information** to solve complex real-world problems *(C4, M4)*

3. **Use appropriately selected tools and processes** to design, test, analyse and evaluate computer systems and identify limitations *(C12, M12, C13, M13)*

4. **Identify and analyse ethical and sustainability concerns** when designing and implementing software and make reasoned decisions informed by ethical frameworks and codes of conduct to minimise adverse impacts *(C7, M7, C8, M8)*

5. **Identify and interpret risk** assessing the potential impact and plan mitigation approaches *(C9, M9, C10, M10)*

6. **Apply and discuss the principles of quality management** in software engineering and systems design *(C14, M14)*

7. **Operate effectively as a member of a team** in various roles *(C16, M16)*

8. **Apply and discuss engineering management principles** demonstrating awareness of commercial context, project and change management, and relevant legal matters *(C15, M15)*

9. **Communicate effectively complex topics** related to programming and software engineering to technical and non-technical audiences *(C17, M17)*

10. **Reflect on their level of mastery** of subject knowledge and skills and plan for personal development *(C18, M18)*

**HCI contribution**: Weeks 6-11 primarily contribute to outcomes **1, 2, 3, 4, 7, 9, and 10**.

---

## HCI-Specific Learning Outcomes (Weeks 6-11)

These **13 outcomes** are specific to the **HCI component** (Weeks 6-11). They elaborate on how the module-wide outcomes are achieved through HCI activities. They are numbered **LO1 through LO13** and should be referenced consistently across all lab materials.

**Source**: `learning-outcomes-condensed.md`

### LO1: Differentiate people-centred design and evaluation methodologies
**Description**: Distinguish between design methodologies (e.g., participatory design, user-centred design) and evaluation methods (e.g., heuristic evaluation, task-based testing, A/B testing).

**Evidenced in**: Week 6 Lab 2 (needs-finding, job stories), Week 9 Lab 1 (evaluation planning, method selection)

**ACM strands**: Understanding People (UP/1-3), HCI-Evaluation (HC/5)

---

### LO2: Design and conduct needs-finding activities
**Description**: Plan and run structured needs-finding sessions (interviews, observation, job story extraction) following ethical protocols including informed consent.

**Evidenced in**: Week 6 Lab 2 (job stories, consent forms), Week 7 Lab 1 (user scenarios)

**ACM strands**: Understanding People (UP/1-2), Requirements Engineering

---

### LO3: Analyse ethical implications of design decisions
**Description**: Identify ethical concerns (privacy, consent, data retention, bias) in interface designs and propose mitigation strategies informed by frameworks like UK GDPR, BCS Code of Conduct.

**Evidenced in**: Week 7 Lab 1 (consent modal, privacy by design), Week 6 Lab 2 (data handling), Week 10 Lab 2 (inclusive redesign)

**ACM strands**: Social and Professional Issues (SP/1-2), Privacy & Security

---

### LO4: Evaluate software interfaces for accessibility concerns
**Description**: Conduct accessibility audits using automated tools (axe DevTools) and manual testing (keyboard navigation, screen readers) against WCAG 2.2 AA standards.

**Evidenced in**: Week 7 Lab 2 (accessibility audit, WCAG mapping), Week 8 Lab 2 (no-JS verification), Week 10 Lab 2 (regression testing)

**ACM strands**: Designing Interaction (HC/2), Accessibility (WCAG 2.2 AA)

---

### LO5: Create interface prototypes using appropriate fidelity levels
**Description**: Build prototypes ranging from paper sketches to functional HTML/HTMX implementations, selecting fidelity appropriate to the design question.

**Evidenced in**: Week 8 Lab 1 (HTMX prototypes, partials), Week 10 Lab 2 (redesign implementation)

**ACM strands**: Prototyping Techniques (HC/3)

---

### LO6: Apply iterative design processes
**Description**: Execute design-test-refine cycles, incorporating evaluation findings into redesigns and documenting rationale with evidence.

**Evidenced in**: Week 9 Lab 2 (pilots, debrief), Week 10 Lab 1 (analysis, prioritisation), Week 10 Lab 2 (redesign, re-verification)

**ACM strands**: Iterative Design (HC/4)

---

### LO7: Analyse how design constraints affect interface decisions
**Description**: Identify technical, accessibility, and no-JS constraints; explain trade-offs in design decisions (e.g., progressive enhancement vs SPA, server-first vs client-first).

**Evidenced in**: Week 8 Lab 1 (pagination, filtering constraints), Week 8 Lab 2 (no-JS parity, routing trade-offs)

**ACM strands**: Design Constraints (HC/4), Software Architecture

---

### LO8: Design and execute appropriate evaluation methods
**Description**: Develop task-based evaluation protocols, define metrics (time-on-task, errors, SUS), run pilots with n=4 participants, and analyse quantitative/qualitative data.

**Evidenced in**: Week 9 Lab 1 (evaluation plan, metrics, instrumentation), Week 9 Lab 2 (pilots, observer notes), Week 10 Lab 1 (data analysis)

**ACM strands**: HCI-Evaluation (HC/5), Empirical Methods

---

### LO9: Apply universal and inclusive design principles
**Description**: Design interfaces that work for diverse users (keyboard-only, screen reader, low vision, cognitive differences) using techniques like semantic HTML, ARIA, focus management.

**Evidenced in**: Week 7 Lab 2 (inclusive fix), Week 8 Lab 2 (no-JS parity), Week 10 Lab 2 (inclusive redesign)

**ACM strands**: Accessibility (HC/2), Universal Design (UP/3)

---

### LO10: Critique potential impacts of designs on society
**Description**: Analyse societal implications of design choices (surveillance, exclusion, environmental cost) and propose alternatives that reduce harm.

**Evidenced in**: Week 7 Lab 1 (ethics analysis), Week 6 Lab 2 (privacy considerations)

**ACM strands**: Social and Professional Issues (SP/1-2), Ethics

---

### LO11: Collaborate effectively in multidisciplinary teams
**Description**: Work in teams using version control (Git), code review, and shared documentation; communicate design rationale to technical and non-technical peers.

**Evidenced in**: Week 9 Lab 2 (peer pilots, observer role), Week 11 Lab 1 (studio crit, peer feedback)

**ACM strands**: Teamwork (Professional Skills)

---

### LO12: Demonstrate professional dispositions
**Description**: Show responsibility (meet deadlines, follow protocols), integrity (cite sources, report honestly), and respect (use people-centred language, honour consent).

**Evidenced in**: All labs (consent adherence, evidence-based claims, inclusive language)

**ACM strands**: Professional Skills, Ethics

---

### LO13: Integrate people-centred design with SE lifecycle
**Description**: Embed HCI practices (needs-finding, evaluation, iteration) within software engineering workflows (version control, testing, deployment).

**Evidenced in**: Week 8 Lab 1 (server-first patterns), Week 9 Lab 1 (instrumentation in code), Week 10 Lab 2 (regression testing)

**ACM strands**: Software Engineering Processes, Requirements Engineering

---

## Cross-Reference: LOs to Weeks & Activities

This table maps each HCI Learning Outcome to specific weeks, labs, and deliverables. Use this for curriculum planning, assessment design, and student progress tracking.

| LO | Outcome (condensed) | Primary Evidence | Secondary Evidence | Assessment Task | ACM Strands |
|----|---------------------|------------------|-------------------|-----------------|-------------|
| **LO1** | Differentiate people-centred methods | W6 L2 (needs-finding, job stories) | W9 L1 (evaluation methods) | Task 1: Evaluation plan | UP/1-3, HC/5 |
| **LO2** | Design and conduct needs-finding | W6 L2 (job stories, consent) | W7 L1 (scenarios) | Backlog (user needs) | UP/1-2 |
| **LO3** | Analyse ethical implications | W7 L1 (consent modal, GDPR) | W6 L2 (privacy), W10 L2 (bias) | Task 2: Privacy audit | SP/1-2 |
| **LO4** | Evaluate for accessibility | W7 L2 (audit, WCAG map) | W8 L2 (no-JS), W10 L2 (regression) | Task 2: Accessibility fixes | HC/2, WCAG 2.2 AA |
| **LO5** | Create prototypes | W8 L1 (HTMX partials) | W10 L2 (redesign) | Code submissions | HC/3 |
| **LO6** | Apply iterative design | W9 L2 (pilots), W10 (analysis + redesign) | W11 L1 (critique) | Tasks 1 & 2: Full cycle | HC/4 |
| **LO7** | Analyse design constraints | W8 L1 (pagination, filtering) | W8 L2 (no-JS trade-offs) | Task 2: Trade-offs doc | HC/4 |
| **LO8** | Design and execute evaluation | W9 L1 (plan, metrics), W9 L2 (pilots) | W10 L1 (analysis) | Task 1: Pilots & findings | HC/5 |
| **LO9** | Apply inclusive design | W7 L2 (inclusive fix) | W8 L2 (no-JS), W10 L2 (redesign) | Task 2: WCAG compliance | HC/2, UP/3 |
| **LO10** | Critique societal impacts | W7 L1 (ethics overlay) | W6 L2 (privacy) | Reflections | SP/1-2 |
| **LO11** | Collaborate in teams | W9 L2 (peer pilots) | W11 L1 (studio crit) | Peer feedback forms | Professional Skills |
| **LO12** | Demonstrate professionalism | All labs (consent, citations) | Portfolio (integrity) | All submissions | Professional Skills |
| **LO13** | Integrate HCI with SE | W8 L1 (server patterns), W9 L1 (instrumentation) | W10 L2 (regression) | Codebase quality | SE Processes |

**How to use this table**:
- **Curriculum planning**: Ensure each LO has sufficient coverage across weeks
- **Student progress**: Track which LOs are being addressed in each lab
- **Assessment design**: Align rubrics to LOs
- **Accreditation**: Map LOs to ACM/BCS requirements

---

## Module Aims (Not Learning Outcomes)

These **four aims** appear on the homepage (`index.md`). They are **broad aspirational statements** about what the module enables students to do. They are **not** the same as Learning Outcomes (which are specific and measurable).

**Current wording** (to be updated with correct terminology):

1. Apply HCI principles to design inclusive interfaces
2. Evaluate accessibility and ethics in interactive systems
3. Implement server-first architecture with progressive enhancement
4. Communicate design decisions with evidence

**Recommendation**: Relabel this section on the homepage as **"Module Aims"** (not "Learning Outcomes") and add a link to this reference page.

---

## Terminology Clarification

### Module Aims
**Definition**: Broad, aspirational statements about what the module sets out to achieve.

**Characteristics**:
- High-level, conceptual
- Focus on "enabling" or "equipping" students
- Not directly assessed (but outcomes derived from aims are assessed)

**Example**: "This module aims to enable students to design accessible web interfaces."

### Learning Outcomes (LOs)
**Definition**: Specific, measurable competencies that students will demonstrate by the end of the module.

**Characteristics**:
- Use action verbs (apply, analyse, design, evaluate)
- Aligned to Bloom's Taxonomy
- Directly assessed
- Mapped to accreditation standards (ACM, BCS)

**Example**: "LO4: Evaluate software interfaces for accessibility concerns using WCAG 2.2 AA standards."

### Lab Objectives
**Definition**: Session-specific tasks and activities that contribute to achieving Learning Outcomes.

**Characteristics**:
- Concrete, actionable
- Time-bound (this lab session)
- Contribute to one or more LOs

**Example**: "Run an axe DevTools audit and document 5+ WCAG violations" (contributes to LO4).

---

## ACM CS2023 Mapping

The 13 HCI Learning Outcomes map to the following ACM Computer Science 2023 curriculum standards:

### Human-Computer Interaction (HC)
- **HC/2**: Designing Interaction (LO4, LO9)
- **HC/3**: Prototyping Techniques (LO5)
- **HC/4**: Iterative Design (LO6, LO7)
- **HC/5**: Evaluation (LO1, LO8)

### Understanding People (UP)
- **UP/1**: User Research (LO1, LO2)
- **UP/2**: Needs Finding (LO2)
- **UP/3**: Accessibility & Universal Design (LO9)

### Social and Professional Issues (SP)
- **SP/1**: Ethics in Computing (LO3, LO10)
- **SP/2**: Privacy & Security (LO3)
- **SP/Professional Skills**: Teamwork, Communication (LO11, LO12)

### Software Engineering Processes (SEP)
- **SEP/2**: Requirements Engineering (LO2, LO13)
- **SEP/3**: Design Patterns & Architecture (LO7, LO13)

### Web & Mobile Systems
- **Server-first patterns** (LO5, LO7, LO13)
- **Progressive enhancement** (LO7, LO9)

---

## WCAG 2.2 Mapping

Learning Outcomes with direct WCAG 2.2 AA compliance requirements:

| LO | WCAG Principles | Key Success Criteria |
|----|----------------|----------------------|
| **LO4** | All (Perceivable, Operable, Understandable, Robust) | 1.3.1 Info & Relationships, 1.4.3 Contrast, 2.1.1 Keyboard, 2.4.7 Focus Visible, 4.1.2 Name/Role/Value |
| **LO9** | Operable, Understandable | 2.1.1 Keyboard, 2.4.1 Skip Links, 2.4.3 Focus Order, 3.3.2 Labels, 4.1.3 Status Messages |

---

## For Students: Self-Assessment

Use this checklist to track your progress across the 13 HCI Learning Outcomes:

| LO | Outcome | Confidence (1–5) | Evidence Location |
|----|---------|------------------|-------------------|
| LO1 | Differentiate people-centred methods | ☐☐☐☐☐ | W6 L2 job stories, W9 L1 eval plan |
| LO2 | Design and conduct needs-finding | ☐☐☐☐☐ | W6 L2 consent protocol |
| LO3 | Analyse ethical implications | ☐☐☐☐☐ | W7 L1 consent modal, privacy audit |
| LO4 | Evaluate for accessibility | ☐☐☐☐☐ | W7 L2 axe audit, WCAG map |
| LO5 | Create prototypes | ☐☐☐☐☐ | W8 L1 HTMX features |
| LO6 | Apply iterative design | ☐☐☐☐☐ | W9 L2 pilots → W10 L2 redesign |
| LO7 | Analyse design constraints | ☐☐☐☐☐ | W8 L2 no-JS trade-offs doc |
| LO8 | Design and execute evaluation | ☐☐☐☐☐ | W9 L1 metrics + W9 L2 pilots |
| LO9 | Apply inclusive design | ☐☐☐☐☐ | W7 L2 fix, W10 L2 redesign |
| LO10 | Critique societal impacts | ☐☐☐☐☐ | W7 L1 ethics reflection |
| LO11 | Collaborate in teams | ☐☐☐☐☐ | W9 L2 peer pilots, W11 L1 crit |
| LO12 | Demonstrate professionalism | ☐☐☐☐☐ | All labs: consent, citations |
| LO13 | Integrate HCI with SE | ☐☐☐☐☐ | W8 L1 Ktor patterns, W9 L1 instrumentation |

**Confidence scale**: 1 = Not confident, 3 = Moderately confident, 5 = Very confident

---

## For Teaching Staff: Using This Reference

### Curriculum Design
- Ensure each LO has ≥2 touchpoints across weeks
- Balance formative (practice) and summative (assessed) evidence
- Check ACM/WCAG coverage for accreditation

### Assessment Design
- Tasks 1 & 2 should collectively assess all 13 LOs
- Use cross-reference table to verify coverage
- Map rubric criteria to specific LOs

### Student Support
- Link to this reference in feedback ("see LO4 for accessibility criteria")
- Use self-assessment checklist in tutorials
- Explain aims vs outcomes vs objectives in Week 6

### Quality Assurance
- Annual review: verify LO mappings still accurate
- External examiner reports: reference this document
- Student feedback: check if LO structure is clear

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-10-15 | Initial definitive reference created; standardized terminology; mapped 13 HCI LOs + 10 module LOs |

---

**Questions?** See [Glossary](glossary.md) for term definitions or contact module lead.
