# AI Champions Hackathon -- Participant Instructions

## 1. Purpose of the Hackathon

The goal of this hackathon is to identify engineers who can effectively use AI development tools to accelerate software development while maintaining strong engineering practices.

Participants will solve a real engineering problem using AI tools. Evaluation will focus not only on producing a working solution but also on engineering quality, testing, architecture, and prompt engineering maturity.

## 2. Hackathon Format

- Participation is **individual** (no teams).
- All participants receive the same problem statement.
- Participants may use AI tools such as ChatGPT, Cursor, Copilot, or similar tools.
- The hackathon duration is **4 hours** unless otherwise announced.
- All submissions must be uploaded to the assigned Google Drive folder before the deadline.
- All solutions must use **Java language**.

## 3. Timeline

| Phase | Event | Time |
|-------|-------|------|
| Problem 1 | Participants join the session | 09:45 |
| | Problem statement released / Hackathon starts | 10:00 |
| | Submission deadline (locked) | 14:00 |
| Problem 2 | Problem statement released | 15:00 |
| | Submission deadline (locked) | 19:00 |

Completion time is measured from the official hackathon start time until the time the submission is uploaded to Google Drive.

## 4. Where to Submit Your Solution

A Google Drive folder will be shared with all participants. Each participant will have a dedicated folder: `<employee-code>`

Example: `208` (employee-code)

You must upload your submission inside your assigned folder only. Once hackathon time is up, Google folders will be locked.

## 5. Required Submission Structure

```
employee-code/phase1/
    code.zip
    prompt-log.md
    architecture.md
```

## 6. Code Submission

Recommended contents:
- Source code
- Unit tests
- Build files (pom.xml, application.yaml, etc.)
- README
- Include the `.git` folder so evaluators can review commit history.

## 7. Prompt Log (prompt-log.md)

This file is required to evaluate how effectively you used AI tools.

Include:
- Prompts you used
- Iterations you performed
- AI tools used (ChatGPT, Cursor, Copilot, etc.)
- Prompts that produced the final solution

## 8. Architecture Document (architecture.md)

Maximum length: **1 page**.

Explain:
- System design
- Key components
- Important decisions made
- Scalability considerations

## 9. Evaluation Criteria

Your solution will be evaluated across five dimensions:

| Criterion | Weight |
|-----------|--------|
| Speed | 10% |
| Testing & Reliability | 30% |
| Prompt Engineering | 10% |
| System Validation | 40% |
| Documentation | 10% |

## 10. Submission Rules

- Upload all required files before the deadline.
- Missing required files may result in scoring penalties.
- Submissions after the deadline will not be considered.
- Do not modify files after the submission deadline.

## 11. Best Practices

- Use AI tools effectively but verify generated code.
- Focus on clean architecture and modular design.
- Write tests for full coverage and functionality.
- Handle edge cases and errors.
- Ensure your project can be run easily by evaluators. If need be you can use either in-memory database or Docker.

## 12. What Happens After the Hackathon

Submissions will be analyzed using automated tools and manual review. Top participants will be selected as the first batch of AI Champions.
