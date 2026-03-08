# Privacy by Design

## What is Privacy by Design?

**Privacy by Design** is an approach to system development that embeds privacy and data protection into the entire lifecycle of technologies, from the earliest design stages through to deployment and beyond. Rather than treating privacy as an afterthought or compliance checkbox, it becomes a core design principle that shapes every decision.

The concept was developed by [Dr Ann Cavoukian](https://iapp.org/media/pdf/resource_center/pbd_implement_7found_principles.pdf) in the 1990s and has since become a foundational principle in data protection legislation, including GDPR (General Data Protection Regulation).

## Core Principles

### 1. Proactive not Reactive; Preventative not Remedial
- Anticipate and prevent privacy risks before they occur
- Don't wait for privacy breaches to happen before taking action
- Design systems that cannot easily leak or misuse personal data

### 2. Privacy as the Default Setting
- Participants' privacy should be protected automatically
- No action required from the participant to protect their privacy
- Systems should work with minimal data collection by default

### 3. Privacy Embedded into Design
- Privacy is integral to the system, not a bolt-on feature
- Not an add-on, not an afterthought
- Becomes a core functional requirement

### 4. Full Functionality – Positive-Sum, not Zero-Sum
- Privacy doesn't require trade-offs with functionality
- Both privacy and functionality can be achieved
- False dichotomy between "usable" and "private"

### 5. End-to-End Security – Full Lifecycle Protection
- Strong security measures from data collection to destruction
- Data minimisation at every stage
- Secure deletion when data is no longer needed

### 6. Visibility and Transparency
- Keep systems open and accountable
- Operations remain visible to participants and stakeholders
- Trust but verify approach

### 7. Respect for Participant Privacy
- Keep person-centred focus
- Give participants control over their data
- Make privacy the default, but allow informed choices

## Why Privacy by Design Matters

### 1. **Legal Compliance**
- GDPR requires Privacy by Design (Article 25)
- UK Data Protection Act 2018 codifies these principles
- Non-compliance can result in significant fines (up to 4% of global turnover or €20 million)
- Better to build it in than retrofit later

### 2. **Ethical Responsibility**
- As software engineers, we have power over people's data
- With that power comes responsibility to protect it
- People have a right to privacy, autonomy, and dignity
- Our systems should respect those rights by default

### 3. **Trust and Reputation**
- Privacy breaches destroy trust
- Trust takes years to build, seconds to lose
- Privacy-respecting systems build confidence
- Competitive advantage in privacy-conscious markets

### 4. **Security Benefits**
- Less data collected = smaller attack surface
- Fewer high-value targets for attackers
- Reduced impact if a breach does occur
- Minimises risk exposure for both participants and organisation

### 5. **Cost Efficiency**
- Cheaper to build privacy in from the start
- Retrofitting privacy is expensive and often incomplete
- Reduces storage and processing costs (less data to manage)
- Avoids costly breach responses and legal fees

### 6. **Participant Empowerment**
- People should control their own data
- Informed consent requires clear, simple choices
- Privacy by Design enables genuine agency
- Respects human autonomy and dignity

## Privacy by Design in COMP2850 HCI

In this module, we practise Privacy by Design through several concrete patterns:

### Data Minimisation

**What we do:**
- Collect only anonymous session IDs (6-8 characters)
- Use random request IDs instead of personally identifiable information
- No names, emails, IP addresses, or other PII (Personally Identifiable Information) in logs
- No accounts, authentication, or persistent profiles

**Why:**
```csv
# Our logs look like this:
ts_iso,session_id,request_id,task_code,step,outcome,ms,http_status,js_mode
2024-10-13T14:23:01Z,abc123xy,req-8f7g,T1_filter,start,success,234,200,true

# NOT like this (bad example):
ts_iso,user_email,user_name,ip_address,task_code,outcome,ms
2024-10-13T14:23:01Z,alice@email.com,Alice Smith,192.168.1.42,T1_filter,success,234
```

We can still measure usability metrics (completion time, error rates) without knowing *who* the person is.

### Local Storage Only

**What we do:**
- All metrics stored in local CSV files on University of Leeds OneDrive (covered by institutional ethical consent)
- No cloud services, no external analytics
- No third-party tracking scripts
- Data stays within UoL-controlled infrastructure

**Why:**
- Reduces risk of data exposure through third parties
- No terms-of-service surprises from external vendors
- Clear data lifecycle (we control retention and deletion)
- Compliance is simpler when data doesn't leave our infrastructure
- UoL OneDrive provides secure, GDPR-compliant storage with appropriate access controls

### Peer-Only Testing Protocol

**What we do:**
- Usability testing only with course peers
- Module-wide blanket consent (everyone knows they may be observed)
- No external participants who might not understand context
- No recordings (video/audio)

**Why:**
- Creates safe learning environment
- Everyone understands the educational purpose
- Reduces power imbalance (peers, not vulnerable populations)
- Minimal risk because everyone is consenting participant

### Evidence Without PII

**What we do:**
- Screenshots must be cropped to show only interface
- Personal information scrubbed from any evidence
- No images of people
- Alt text required for accessibility, but no identifying details
- Use pseudoanonymisation (e.g., "Participant A", "Session 1") when reporting qualitative data from interviews, recordings, or transcription

**Why:**
- Evidence is about interface design, not people
- Respects dignity and consent of participants
- Prevents accidental identification years later
- Forces focus on the system, not the person using it
- **Note**: Full anonymisation is often impossible for qualitative research; pseudoanonymisation (removing direct identifiers while retaining links for analysis) is typically the best achievable practice for interviews, recordings, transcription, and quantitative analysis

### Transparent Research Protocol

**What we do:**
- Clear written protocol explaining what data is collected
- Explicit task descriptions and measures
- Right to withdraw at any time
- Minimal task time caps (3 minutes max to avoid frustration)

**Why:**
- Informed consent requires transparency
- People can only consent to what they understand
- Respects participant autonomy
- Builds trust in research process

### No Feature Creep

**What we do:**
- Don't add "helpful" tracking features
- Resist temptation to add "just one more field"
- Question every data point: is this necessary?
- Start with minimal data, expand only if justified

**Why:**
- Scope creep applies to data collection too
- Each new field is a new privacy risk
- "We might need it someday" is not justification
- Constraints breed creativity (work within limits)

## Practical Examples from Our Labs

### Week 9: Server-Side Instrumentation

**Privacy-Respecting Approach:**
```kotlin
// Log only what's needed for metrics
Logger.log(
    sessionId = "abc123xy",      // Anonymous cookie
    requestId = "req-8f7g",       // Random per-task
    taskCode = "T1_filter",       // Which task
    step = "start",               // What happened
    outcome = "success",          // Result
    ms = 234L,                    // Duration
    httpStatus = 200,             // HTTP response
    jsMode = "true"               // JS enabled/disabled
)
```

**What we DON'T log:**
- Browser user agent strings (can fingerprint devices)
- Full URLs (might contain personal data in query params)
- Form input values (might be personal information)
- Mouse movements or keystroke timings (surveillance-like)
- IP addresses (can identify individuals)

### Week 10: Analysis & Evidence

**Privacy-Respecting Analysis:**
```kotlin
// Aggregate data, analyze patterns, no individuals
data class TaskStats(
    val taskCode: String,
    val medianMs: Double,
    val completionRate: Double,
    val errorRate: Double
)
```

We report: "Task T1 had a median completion time of 8.2 seconds with a 90% completion rate."

We DON'T report: "Session abc123xy took 15 seconds and made 3 errors."

**Why:** The goal is to improve the interface, not judge individuals.

### Week 11: Portfolio & Submission

**Privacy-Respecting Evidence:**
- Screenshots cropped to show only UI elements
- No usernames visible in interface
- No timestamps that could identify sessions
- Generic task data ("rename task A to task B")

**Privacy-Violating Evidence (Don't do this):**
- Full-screen screenshots showing participant's desktop
- Visible personal calendar events or email notifications
- Identifiable profiles in test data
- Timestamped evidence linking to specific people

## Common Myths About Privacy

### Myth 1: "We're not collecting sensitive data, so privacy doesn't matter"
**Reality:** What seems innocuous can become sensitive in aggregate or context. Session patterns, timing data, and behavioural metrics can reveal sensitive information. Privacy by Design applies to all data.

### Myth 2: "Privacy and usability are in conflict"
**Reality:** Privacy by Design is about smart design choices, not removing features. Anonymous session IDs work just as well as personal accounts for our use case. Good UX respects participants' privacy.

### Myth 3: "We can just anonymise data later"
**Reality:** Anonymisation is hard and often fails. Re-identification attacks are common. Better to never collect identifying data in the first place. True anonymity requires design from the start.

### Myth 4: "People don't care about privacy anyway"
**Reality:** People care deeply when they understand the implications. Surveys show privacy is a top concern. More importantly, privacy is a right, not a popularity contest.

### Myth 5: "We need data to improve the product"
**Reality:** We need *insights*, not personal data. Aggregate metrics, task success rates, and usability findings don't require knowing who anyone is. Data minimisation often leads to better focus.

## Questions to Ask

When designing any system that collects data, ask:

1. **Do we need this data at all?**
   - What decision does it inform?
   - What happens if we don't collect it?
   - Is there a less invasive alternative?

2. **Can we use anonymous or pseudonymous data?**
   - Random session IDs instead of accounts?
   - Request IDs instead of tracking individuals?
   - Aggregate statistics instead of individual records?

3. **How long do we need to keep it?**
   - Set retention policies upfront
   - Delete data when no longer needed
   - Question perpetual storage defaults

4. **Who has access?**
   - Minimize access to those who need it
   - Log access to sensitive data
   - Audit regularly

5. **What could go wrong?**
   - Threat model: what attacks are possible?
   - Data breach: what's the impact?
   - Misuse: could this data harm people?

6. **Can people control their data?**
   - Export their data?
   - Delete their data?
   - Correct inaccuracies?

7. **Is collection transparent?**
   - Do people know what's collected?
   - Do they understand why?
   - Can they make informed choices?

## Resources & Further Reading

### Foundational Documents
- [Privacy by Design: The 7 Foundational Principles](https://iapp.org/media/pdf/resource_center/pbd_implement_7found_principles.pdf) (Ann Cavoukian)
- [GDPR Article 25: Data Protection by Design and by Default](https://gdpr-info.eu/art-25-gdpr/)

### UK Context
- [ICO Guide to Privacy by Design](https://ico.org.uk/for-organisations/guide-to-data-protection/guide-to-the-general-data-protection-regulation-gdpr/accountability-and-governance/data-protection-by-design-and-default/)
- [UK Data Protection Act 2018](https://www.legislation.gov.uk/ukpga/2018/12/contents/enacted)

### Practical Guides
- [OWASP Privacy Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Privacy_Cheat_Sheet.html)
- [Privacy Patterns (design patterns for privacy)](https://privacypatterns.org/)

### Academic Perspectives
- Shirlei Aparecida de Chaves and Fabiane Benitti. 2025. User-Centred Privacy and Data Protection: An Overview of Current Research Trends and Challenges for the Human–Computer Interaction Field. ACM Comput. Surv. 57, 7, Article 176 (February 2025), 36 pages. https://doi.org/10.1145/3715903

- Giovanni Iachello and Jason Hong. 2007. End-User Privacy in Human-Computer Interaction. Found. Trends Hum.–Comput. Interact. 1, 1 (January 2007), 1–137. https://www.cs.cmu.edu/~jasonh/publications/fnt-end-user-privacy-in-human-computer-interaction-final.pdf

- Jaap-Henk Hoepman. 2014. Privacy Design Strategies. In ICT Systems Security and Privacy Protection, Nora Cuppens-Boulahia, Frédéric Cuppens, Sushil Jajodia, Anas Abou El Kalam, and Thierry Sans (Eds.). Springer, Berlin, Heidelberg, 446–459. https://doi.org/10.1007/978-3-642-55415-5_38

- George Danezis, Josep Domingo-Ferrer, Marit Hansen, Jaap-Henk Hoepman, Daniel Le Métayer, Rodica Tirtea, and Stefan Schiffner. 2014. Privacy and Data Protection by Design – from policy to engineering. ENISA, European Union Agency for Network and Information Security. https://arxiv.org/abs/1501.03726

## Summary: Why This Matters for HCI

As HCI practitioners, we design the interfaces and systems that mediate people's digital lives. Every interaction we design, every feature we implement, has privacy implications.

**Privacy by Design is good HCI because:**

1. **Respects human dignity** - People are not data points
2. **Builds trust** - Essential for meaningful human-computer interaction
3. **Reduces cognitive burden** - People shouldn't need law degrees to protect themselves
4. **Enables inclusion** - Privacy concerns disproportionately affect marginalised groups
5. **Future-proofs systems** - Privacy-respecting design ages better
6. **Aligns with ethics** - Core ACM Code of Ethics principle (1.6: Respect privacy)

In COMP2850, we practise Privacy by Design not because it's required for coursework, but because it's required for **responsible software engineering**. The habits you form now shape the systems you'll build throughout your career.

**The question is not "How much data can we collect?"**

**The question is "How little data do we need to achieve our goals?"**

Privacy by Design starts with that question.
