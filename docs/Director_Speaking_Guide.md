# Director-Level Presentation Script

> **Duration:** ~12-15 minutes | **Audience:** Director & Manager | **Tone:** Confident, clear, no jargon

---

## Before You Start — Mindset

- Directors don't care about code. They care about **impact**, **initiative**, and **growth**.
- Speak like you're telling a story to a senior colleague — not reading a textbook.
- If you don't know an answer, say: *"That's a great question. I'd need to look into that and get back to you."*
- Keep it conversational. Pause between slides. Make eye contact.

---

## Slide 1 — Title *(30 seconds)*

> **Walk in, smile, and start:**

"Good morning/afternoon, everyone. Thank you for taking the time today.

I'm Manprit Singh Panesar — I've been interning here since May 2025 under Gautom sir's team, working closely with Sunil sir on architecture and Viresh sir as my day-to-day mentor.

Over the past year, my focus has been on **building CI/CD automation solutions** — essentially making the build and deployment process faster and more standardized for the teams. Let me walk you through what I've done."

---

## Slide 2 — About Me *(45 seconds)*

> **Keep it light and personal. Directors like to know the person, not just the work.**

"A quick bit about me — I'm from Jamshedpur, Jharkhand. I'm pursuing B.Tech in Computer Science from Arka Jain University.

My core interest is in **backend development and CI/CD automation** — basically how we get code from a developer's machine to production reliably.

Outside of work, I enjoy art and design, traveling, and playing badminton. I believe these interests actually help me think more creatively when solving technical problems."

> **Don't rush this slide. Let them connect with you as a person.**

---

## Slide 3 — Roadmap *(1 minute)*

> **Give them the big picture before diving into details.**

"Here's a high-level view of my one-year journey, broken down by quarters.

In **Q1**, I focused on learning the fundamentals — Jenkins, Docker, scripting — and delivered my first project, which was automating repository cleanup.

In **Q2**, I moved into something more impactful — I built a **Shared Library** called UnifiedCI that standardizes how Java and Python projects are built across teams.

**Q3** was about extending that to React projects and also exploring how AI and ML can help in CI/CD — specifically something called Jenkins MCP.

And right now in **Q4**, I'm working on **two things** — an Android CI/CD pipeline and an **ML-powered system that predicts which AWS EC2 instance is best** for running a particular build.

So the journey has been — learn, build, extend, and now innovate."

> **That last line is your anchor. Directors love hearing growth trajectories.**

---

## Slide 4 — Q1: Foundation & POC 1 *(1.5 minutes)*

"So starting with Q1 — this was my learning and onboarding phase.

**What was the problem?** — When teams finish working on features, they create pull requests. Over time, many of these pull requests become stale — nobody's reviewing them, the branches are just sitting there. Cleaning this up manually was taking time.

**What I built** — I created an automated system using Jenkins that scans all pull requests, identifies the stale ones, labels them, closes them, and deletes the associated branches. So what someone used to do manually every week, now happens automatically.

**What I learned technically** — Jenkins pipelines, Docker, Groovy and Python scripting. These became the foundation for everything I built later.

**On the soft skills side** — this was my first time in a corporate IT environment. I learned how communication works here — how to raise requests, how to write professional emails, how to follow up properly.

**The real challenges I faced** — honestly, the hardest part wasn't the coding. It was getting access. Getting my desktop set up took time. GitHub access had to go through approvals. Setting up the Linux PC for Jenkins and Docker required separate permissions. And then we needed SWAN2 network access so the Linux machine could actually connect to everything. These things seem small, but they took real time and patience to resolve."

---

## Slide 5 — Q2: UnifiedCI (POC 2) *(1.5 minutes)*

"Q2 is where things got interesting.

**The problem I noticed** — every team was writing their own Jenkins pipeline from scratch. The Java team had one way of doing it, the Python team had a completely different setup. There was no consistency, and every new project meant someone had to figure out CI/CD all over again.

**What I built** — I created **UnifiedCI** — a centralized Shared Library. Think of it like a template system. A team just tells it 'I'm a Java project using Maven' or 'I'm a Python project', and it automatically sets up the entire build, test, and reporting pipeline. No need to write hundreds of lines of Jenkins code anymore.

**The impact** — we went from each team writing 200-300 lines of pipeline code to just 5-10 lines of configuration. That's roughly a **95% reduction** in setup effort.

**Learnings** — technically, I understood how Shared Libraries work in Jenkins, how testing frameworks like JUnit and pytest integrate. On the soft skills side, this was the first project where I had to **collaborate with multiple teams** to understand their needs. I also learned the importance of writing good documentation — because if other teams can't understand how to use it, the whole effort is wasted.

**Challenges** — my Linux PC ran out of disk space because of Docker images piling up. We had to go through the process of getting a new PC provisioned. Also, getting the right tools installed required admin access, which meant more access requests."

---

## Slide 6 — Q3: React + AI/ML *(1.5 minutes)*

"In Q3, I extended UnifiedCI to support **React and React Native projects** — so now it covers Java, Python, and React. That's three tech stacks, one library.

But the more exciting part was the **AI/ML exploration**. I researched something called **Jenkins MCP — Model Context Protocol**. In simple terms, it's a way to connect AI models to Jenkins so they can read build logs, understand what went wrong, and suggest fixes.

Imagine a build fails at 2 AM. Instead of a developer waking up and reading through 500 lines of logs, the AI can summarize what happened and suggest the likely fix. That's the direction we explored.

**Learnings** — technically, I got into the React ecosystem — npm, Jest for testing, and how frontend CI differs from backend. I also learned about MCP architecture and how LLMs can be connected to tools. On the soft skills side, this was the first time I had to **present my research findings to the team and stakeholders**. That was a big growth moment for me.

**Challenges** — getting the right tool versions approved, setting up the environments, and getting access to AI tools — these all required coordination with IT and approvals."

---

## Slide 7 — Q4: Mobile CI/CD + ML Node Selection *(2 minutes)*

> **This is your current work. Show energy and ownership here.**

"Now, Q4 — this is what I'm currently working on, and it has two parts.

**First — Android CI/CD.** Right now, building and deploying Android apps involves a lot of manual steps. I'm building a pipeline where a developer just pushes code, and Jenkins automatically builds the app inside a Docker container, runs tests, and can deploy to the Play Store. The Docker environment, the build pipeline — these are working. Play Store deployment is the piece I'm finishing up.

**Second — and this is something I'm really excited about — ML-powered EC2 node selection.** Here's the idea: when Jenkins runs a build, it needs a machine — an EC2 instance on AWS. Right now, teams either pick the same instance every time or guess what they need. Sometimes the instance is too small and the build is slow. Sometimes it's too big and we're wasting money.

**What I'm building** is a prediction model that looks at the project type, the repo size, the build history, and recommends the right EC2 instance. I've already generated **synthetic training data** to build the initial model, and I'm now working on improving the prediction accuracy with real-world data.

**Learnings** — Android CI/CD patterns, Fastlane for deployment, and on the ML side, how to prepare datasets and think about prediction models. Soft skills wise, this quarter has really tested my ability to **work independently** and manage my own timelines.

**Challenges** — Play Store requires production credentials and signing certificates, which need approvals. For the ML work, getting access to real AWS EC2 usage data has been a process."

---

## Slide 8 — Architecture Overview *(1 minute)*

> **Keep this high-level. Directors don't need to know function names.**

"This slide gives you a visual overview.

**On the left** — this is UnifiedCI's architecture. At the top, we have entry points for each language — Java, Python, React. They all feed into a shared core that handles build, test, linting, and reporting. So the logic is written once and reused everywhere.

**On the right** — two things. The Android pipeline flows from Jenkins to a Docker container to the Play Store. And the ML node selection system takes project data, runs it through the prediction model, and recommends which EC2 instance to use.

The key takeaway here is — **everything is modular and reusable**. Nothing is hardcoded for one team or one project."

---

## Slide 9 — Key Achievements *(1 minute)*

"Let me quickly summarize the numbers.

**4 POCs delivered** throughout the internship — from basic automation to ML-powered solutions.

**4 tech stacks** supported — Java, Python, React, and Android.

**95% reduction** in pipeline setup code — teams go from writing hundreds of lines to just a few lines of config.

**Setup time went from days to hours** — a new project that used to take 2-3 days to set up CI/CD can now be done in a couple of hours.

I've also maintained comprehensive documentation — architecture docs, API references, onboarding guides, and configuration examples for every POC."

---

## Slide 10 — Thank You *(15 seconds)*

"That's the summary of my internship journey. I've gone from learning the basics to building solutions that are being used and are making a real difference.

Thank you for your time. I'm happy to answer any questions."

> **Stop. Smile. Wait for questions. Don't rush to fill silence.**

---

## Q&A Preparation

### Director-Level Questions (Big Picture)

**Q: "What's the business value of what you've built?"**
> "The biggest value is standardization and time savings. Before UnifiedCI, every team was building their own pipeline — that meant duplicated effort, inconsistent quality, and slow onboarding. Now, a new project can be set up in hours instead of days, and every project follows the same quality standards."

**Q: "How does the ML EC2 selection save money?"**
> "Right now, teams often pick larger instances than needed — just to be safe. That costs money. The prediction model recommends the right-sized instance based on the actual project requirements. Even saving one instance size level across multiple daily builds adds up significantly over a year."

**Q: "Can this scale across the organization?"**
> "Yes, that was a key design decision. UnifiedCI is built as a Shared Library — any team in the org can use it by adding a few lines of config. It already supports three tech stacks, and the architecture is modular enough to add more."

**Q: "What happens after your internship? Who maintains this?"**
> "I've documented everything thoroughly — architecture, setup guides, API references. The codebase is in the team's GitHub repository. Any developer familiar with Jenkins can pick it up. I've also done knowledge transfer sessions with the team."

**Q: "What would you do differently if you started over?"**
> "I'd push for environment access earlier. A lot of time in Q1 went into getting GitHub access, Linux PC setup, and network access. If I had started those requests from day one, I could have been productive sooner. On the technical side, I'd design the Shared Library with the ML component in mind from the beginning, rather than adding it later."

---

### Manager-Level Questions (Technical Depth)

**Q: "How does the language auto-detection work?"**
> "The library scans the project root for marker files — if it finds pom.xml or build.gradle, it's Java. If it finds requirements.txt or setup.py, it's Python. If it finds package.json with React dependencies, it's React. It's a simple but reliable approach."

**Q: "What data does the ML model use for predictions?"**
> "Currently, I've built a synthetic dataset that includes project type, repository size, number of dependencies, historical build duration, and memory usage. The model uses these features to predict the optimal EC2 instance type. I'm working on incorporating real build metrics to improve accuracy."

**Q: "How did you handle the Docker disk space issue?"**
> "Docker images were accumulating on the Linux PC. Short term, I implemented cleanup scripts to prune unused images. Long term, the team provisioned an additional PC with more storage. I also set up automated cleanup in the pipeline itself."

**Q: "What testing have you done on UnifiedCI?"**
> "Each language pipeline has been tested with sample projects. For Java, I tested with both Maven and Gradle projects. For Python, with pip-based projects. The library also has built-in error handling — if something fails, it gives clear error messages rather than cryptic Jenkins failures."

**Q: "How does Jenkins MCP actually work?"**
> "Think of MCP as a bridge. On one side, you have an AI model. On the other side, you have Jenkins. MCP lets the AI model read Jenkins data — build logs, pipeline status, test results — and provide intelligent analysis. It's like giving the AI model 'eyes' into your CI/CD system."

---

## Pro Tips for the Presentation

| Do | Don't |
|---|---|
| Pause after each slide | Rush through content |
| Say "we" when talking about team work | Say "I" for everything |
| Smile and make eye contact | Read from your notes |
| Use phrases like "the idea is..." | Use jargon like "vars directory" |
| Admit what's still in progress | Pretend everything is done |
| Say "that's a great question" before answering | Answer immediately without thinking |

### Power Phrases to Use:
- *"The core problem was..."*
- *"What this means in practice is..."*
- *"The real impact here is..."*
- *"One thing I'm particularly proud of is..."*
- *"To put it simply..."*
- *"From a business perspective..."*
- *"I worked closely with the team on..."*

### Phrases to Avoid:
- ~~"Basically..."~~ (sounds unsure)
- ~~"Actually..."~~ (sounds like you're correcting yourself)
- ~~"I think..."~~ → Say **"In my experience..."**
- ~~"It's just a..."~~ → Everything you built matters
- ~~"I don't know"~~ → Say **"I'd need to look into that and follow up"**
