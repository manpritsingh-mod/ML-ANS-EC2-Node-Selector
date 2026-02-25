# ML-Based AWS Node Selector — POC Explanation

---

## The Problem We're Solving

Today, when a developer pushes code and a Jenkins build starts, **someone has to decide which server (node) to run it on**. Most teams either:

- **Pick the same server every time** — which wastes money (big server for a small job) or slows things down (small server for a big job)
- **Guess** — "This looks like a heavy build, let's use the big one"

Neither approach is smart. We're either **overpaying AWS** or **making developers wait**.

---

## What This POC Does (One Line)

> **It teaches Jenkins to automatically pick the right-sized AWS server for each build — no human guessing.**

---

## How It Works — The Simple Version

Think of it like ordering a cab. You don't want a bus for one person, and you don't want a bike for a family of five. You want the right vehicle for the trip.

**Our system does exactly that, but for build servers:**

### Step 1: "Look at what we're building"

When a developer pushes code, the pipeline automatically checks:
- What kind of project is this? (Java? Python? Mobile app?)
- How big is the change? (5 files changed, or 500?)
- What does the build need to do? (Just compile? Run tests? Deploy?)
- How many dependencies does it have?

This takes about **5 seconds** and happens automatically — no one has to fill in a form.

### Step 2: "Ask the ML model for a recommendation"

All that information (27 data points) gets fed into a trained **Machine Learning model** (Random Forest — a proven, reliable algorithm). The model responds with:

- **How much memory this build will need** (e.g., 4 GB)
- **How much CPU it will use** (e.g., 50%)
- **How long it will take** (e.g., 12 minutes)

### Step 3: "Pick the right server"

Based on the prediction, the system maps it to one of the available AWS server sizes:

| Server Size | Memory | Best For |
|-------------|--------|----------|
| Lightweight | 1 GB | Small scripts, linting |
| Executor | 2 GB | Python builds, small Node.js |
| Build | 8 GB | Java/Maven, standard builds |
| Test | 16 GB | Full test suites, Docker builds |
| Heavy Test | 32 GB | Mobile apps, emulators, E2E tests |

The right server spins up, the build runs, and the server shuts down when done.

---

## Why This Matters to the Business

### 1. Cost Savings
A Java build that needs 4 GB shouldn't run on a 32 GB server. Today, if we over-provision, **we're paying 4–8x more than needed** for that build. Multiply that by hundreds of builds per day.

### 2. Faster Builds
Equally, a mobile app build that needs 16 GB shouldn't be squeezed onto a 2 GB server. That causes it to run slow, swap memory, and sometimes crash. Developers end up **re-running failed builds**, wasting even more time.

### 3. Zero Manual Intervention
No one has to decide "which label should I use for this job." The ML model handles it. New projects get analyzed automatically — no configuration needed.

### 4. It Gets Smarter Over Time
Every build that runs generates real data. We can feed that back into the model to retrain it, making predictions more accurate month after month.

---

## What Does the Pipeline Look Like When It Runs?

```
BUILD #42 — Java-Maven-Testing
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Stage 1: Collect Metadata          [5 sec]
   → Java project, 12 files changed, 8 stages, has unit tests

✅ Stage 2: ML Prediction             [3 sec]
   → Predicted: 4.2 GB memory, 48% CPU, 11 min build time
   → Recommended: "build" node (T3a Large, 8 GB)

✅ Stage 3: Provision AWS Node        [30 sec]
   → Spinning up T3a Large instance...
   → Node ready.

✅ Stage 4–8: Build → Test → Deploy   [11 min]
   → Running on the right-sized server.

TOTAL: ~12 minutes, optimal cost.
```

---

## Current POC Status

| What | Status |
|------|--------|
| Metadata collection (auto-detect project type, tests, dependencies) | ✅ Done |
| ML model trained (27 features, Random Forest) | ✅ Done |
| AWS node recommendation (label mapping) | ✅ Done |
| Jenkins pipeline integration (shared library) | ✅ Done |
| Actual AWS node provisioning | ⏳ Needs AWS EC2 Plugin configured |

**What works today:** The full prediction pipeline runs end-to-end. Jenkins collects metadata, feeds it to the ML model, and gets back a recommendation for which AWS node to use. The system correctly identifies that a Java project with unit tests and integration tests needs a "build" node (8 GB T3a Large).

**What's next:** Once the AWS EC2 Cloud Plugin is configured in Jenkins with the right AMI templates, the system will automatically spin up the recommended server and run the build on it.

---

## How Is This Different From Static Configuration?

| | Static (Today) | ML-Based (This POC) |
|-----|----------------|---------------------|
| Who decides the server? | DevOps engineer | ML model, automatically |
| Adapts to project changes? | No — manual update | Yes — re-analyzes every build |
| New project onboarding | Configure labels manually | Automatic — detects from code |
| Cost optimization | Same server for everything | Right-sized per build |
| Handles edge cases | Only if someone remembers | Model learns from patterns |

---

## The Technology Behind It (Just Enough Detail)

- **Jenkins Shared Library** — This is a plugin-like package that any Jenkins pipeline can import and use. No changes needed to existing projects — just add one line to their Jenkinsfile.
- **Random Forest Model** — A well-established ML algorithm that makes predictions based on patterns in historical data. It's not a black box — we can see exactly which factors matter most (project type, first build, emulator usage).
- **27 Features** — The model considers 27 real data points about each build (project type, dependencies, git changes, test configuration, cache state, etc.). These are all detected automatically from the code itself.

---

## Key Takeaway

> This POC proves that we can **replace manual guesswork with data-driven decisions** for choosing build infrastructure. The ML model analyzes each build, predicts its resource needs, and picks the right AWS server — saving cost on small builds and preventing failures on large ones.

---

## How the Prediction Actually Works (If Asked for Details)

Here's the step-by-step flow of what happens under the hood when a build starts:

### Step 1 — Auto-Detect the Project *(~5 seconds)*

The moment the pipeline starts, our code automatically scans the project workspace. It looks at what files exist — if there's a `pom.xml`, it knows it's a Java project. If there's a `package.json`, it's Node.js. It also reads the Jenkinsfile itself to count how many stages the build has, whether there are unit tests, integration tests, Docker builds, deployments — all of this without anyone configuring anything.

### Step 2 — Git Analysis *(~2 seconds)*

At the same time, it runs a quick git check — how many files did the developer change? How many lines were added or deleted? Did they change any dependency files? A 5-file change needs very different resources than a 500-file change.

### Step 3 — Build the Feature Set *(instant)*

All of that gets packaged into **27 data points**. Think of it like a form with 27 fields — project type, repo size, number of dependencies, which tests are enabled, is there a Docker build, is it the first build or do we have cache, what time of day it is — 27 factors total.

### Step 4 — ML Prediction *(~3 seconds)*

Those 27 data points get fed into a **Random Forest model**. Random Forest is basically a collection of hundreds of decision trees — each tree looks at the data and makes a guess, then they all vote on the answer. It's the same technique used in fraud detection and recommendation engines — reliable and explainable.

The model outputs three numbers:
- How much **memory** this build will need
- How much **CPU** it will use
- How long it will **take**

### Step 5 — Map to the Right Server *(instant)*

Based on the predicted memory, we map to the right AWS server. If the model says 4 GB, we pick an 8 GB server (we add a 20% safety buffer). If it says 12 GB, we pick the 16 GB server. Simple lookup — five server sizes, pick the closest one that fits.

---

## Common Manager Questions

### "What if the model is wrong?"

We have a fallback. If the ML model fails for any reason — Python isn't installed, model file is missing, anything — the system automatically falls back to a rule-based estimation. It looks at the project type and git changes, and uses a simple formula to estimate resources. So the pipeline never breaks — worst case, it just makes a slightly less optimal choice.

### "How accurate is it?"

Right now the model is trained on synthetic data — realistic but generated — so it's at about 67% accuracy. Once we deploy to production and start collecting real build data (actual CPU, memory, and time from real builds), we can retrain the model. Industry experience shows that with 2-3 months of real data, accuracy jumps to 85-90%.

### "How does it learn and improve?"

Every build that runs generates real metrics — how much memory it actually used, how long it actually took. We log that data. Periodically, we retrain the model with this real data, and it gets better at predicting. It's a feedback loop — predict, measure, learn, predict better.
