# Enhanced ML Node Selector - Revised Implementation Plan

## Goal
Create a **realistic synthetic dataset** based on real CI/CD patterns from Kaggle and industry research, covering all project types, pipeline configurations, and edge cases for accurate resource prediction.

---

## User Review Required

> [!IMPORTANT]
> **Key Decisions Made:**
> 1. ✅ Use Kaggle `ci_cd_logs.csv` as base structure (pipeline IDs, stages, branches, statuses)
> 2. ✅ Enhance with realistic resource metrics based on industry research
> 3. ✅ Cover ALL project types (Python, Java, Node, React Native, Android, iOS)
> 4. ✅ Cover ALL pipeline configurations (unit tests, E2E, Docker, emulators)
> 5. ✅ Create 1000+ records with realistic correlations

---

## Analysis: Available Data Sources

### Source 1: Your Kaggle Dataset (`ci_cd_logs.csv`)
```
Columns: timestamp, pipeline_id, stage_name, job_name, task_name, 
         status, message, commit_id, branch, user, environment

What we can use:
✅ stage_name (Build, Test, Deploy, Analysis)
✅ job_name (build_and_test, run_unit_tests, run_integration_tests, deploy_to_*)
✅ status (success, failed, running, skipped)
✅ branch patterns
✅ environment (development, staging, production)

What's MISSING (need to synthesize):
❌ Build duration/time
❌ CPU usage
❌ Memory usage
❌ Git metrics (files changed, lines added)
❌ Project type
❌ Dependency information
```

### Source 2: Your Current Training Data (`sample_training_dataset.csv`)
```
Columns: build_id, timestamp, branch, build_type, files_changed, 
         lines_added, lines_deleted, deps_changed, cpu_avg, cpu_max, 
         memory_avg_mb, memory_max_mb, build_time_sec, status

This has resource metrics but MISSING:
❌ Project type
❌ Pipeline stages/structure
❌ Test types
❌ Cache state
❌ Emulator/Docker usage
```

---

## Enhanced Dataset Design

### New Schema (30 columns)

| Column | Type | Source | Description |
|--------|------|--------|-------------|
| `build_id` | string | Generate | Unique build identifier |
| `timestamp` | datetime | From Kaggle | Build start time |
| `pipeline_id` | string | From Kaggle | Pipeline identifier |
| `project_type` | int | Synthesize | 0-5 (python/java/node/react-native/android/ios) |
| `repo_size_mb` | float | Synthesize | Repository size |
| `branch` | string | From Kaggle | Branch name |
| `branch_type` | int | Derive | 0=feature, 1=develop, 2=main, 3=hotfix, 4=release |
| `build_type` | int | Synthesize | 0=debug, 1=release |
| `environment` | int | From Kaggle | 0=dev, 1=staging, 2=production |
| **Git Metrics** | | | |
| `files_changed` | int | Synthesize | Files modified |
| `lines_added` | int | Synthesize | Lines added |
| `lines_deleted` | int | Synthesize | Lines deleted |
| `source_files_pct` | float | Synthesize | % source vs config files |
| `deps_file_changed` | int | Synthesize | 0/1 lock file changed |
| `dependency_count` | int | Synthesize | Number of dependencies |
| **Pipeline Structure** | | | |
| `stages_count` | int | From Kaggle | Number of stages |
| `has_build_stage` | int | From Kaggle | 0/1 |
| `has_unit_tests` | int | From Kaggle | 0/1 |
| `has_integration_tests` | int | From Kaggle | 0/1 |
| `has_e2e_tests` | int | Synthesize | 0/1 Appium/Selenium |
| `has_deploy_stage` | int | From Kaggle | 0/1 |
| `has_docker_build` | int | Synthesize | 0/1 |
| `uses_emulator` | int | Synthesize | 0/1 Android/iOS emulator |
| `parallel_stages` | int | Synthesize | Number of parallel stages |
| **Cache State** | | | |
| `is_first_build` | int | Synthesize | 0/1 first build for branch |
| `cache_available` | int | Synthesize | 0/1 node_modules/cache exists |
| **Targets** | | | |
| `cpu_avg_pct` | float | Synthesize | Average CPU % (TARGET) |
| `memory_gb` | float | Synthesize | Peak memory GB (TARGET) |
| `build_time_min` | float | Synthesize | Total build time minutes (TARGET) |
| `status` | string | From Kaggle | success/failed |

---

## Realistic Value Ranges (Based on Industry Research)

### Build Times by Project Type + Pipeline

| Scenario | Build Time Range | Reasoning |
|----------|------------------|-----------|
| Python + Unit Tests | 2-8 min | Fast interpreter, quick tests |
| Python + pip install (no cache) | 5-30 min | Heavy deps like numpy, pandas |
| Java + Maven Build | 5-20 min | Compilation + dependency resolution |
| Java + Full Tests | 15-45 min | Compilation + unit + integration |
| Node.js + npm install (no cache) | 3-25 min | Downloads 100s of packages |
| Node.js + npm install (cached) | 1-3 min | Local node_modules |
| React Native Build (Debug) | 10-25 min | JS bundle + native compile |
| React Native Build (Release) | 20-40 min | Optimization + signing |
| React Native + Appium E2E | 30-90 min | Emulator startup + test execution |
| Android Native (Debug) | 8-20 min | Gradle + incremental |
| Android Native (Release) | 15-35 min | Full build + ProGuard + signing |
| iOS Native | 10-30 min | Xcodebuild |
| Docker Build | 3-20 min | Layer caching varies |

### Memory by Project Type + Pipeline

| Scenario | Memory Range (GB) | Reasoning |
|----------|-------------------|-----------|
| Python Unit Tests | 0.5-2 | Lightweight |
| Java Maven Build | 2-6 | JVM heap |
| Node.js Build | 1-4 | V8 and npm |
| React Native Build | 4-8 | Metro bundler + native |
| Android Emulator Test | 8-16 | Emulator = 4GB+ |
| iOS Simulator | 6-12 | Heavy simulator |
| Docker Build | 2-8 | Container overhead |
| E2E + Parallel Tests | 12-32 | Multiple browsers/emulators |

### CPU by Scenario

| Scenario | CPU % Range | Reasoning |
|----------|-------------|-----------|
| Checkout/Clone | 5-15 | I/O bound |
| npm/pip install | 20-50 | Network + some compilation |
| Compilation (Java/Kotlin) | 60-95 | CPU intensive |
| Unit Tests | 30-70 | Varies by parallelism |
| Integration Tests | 40-80 | DB + services |
| E2E Tests | 50-90 | Browser/emulator rendering |
| Docker Build | 40-85 | Layer compilation |

---

## Synthetic Data Generation Strategy

### Step 1: Parse Kaggle Data for Pipeline Patterns
```python
# Extract unique pipeline configurations from ci_cd_logs.csv
# - What stages are common together
# - Branch naming patterns
# - Success/failure ratios
```

### Step 2: Create Project Type Distributions
```
Distribution (realistic for enterprise):
- Python: 25%
- Java/Kotlin: 25%
- Node.js: 20%
- React Native: 15%
- Android Native: 10%
- iOS Native: 5%
```

### Step 3: Create Pipeline Configuration Matrix
```
For each project type, define realistic pipeline configs:

Python Projects:
├── Unit tests only: 40%
├── Unit + Integration: 35%
├── Full pipeline (lint + test + deploy): 25%

React Native Projects:
├── Debug build only: 20%
├── Debug + Unit tests: 30%
├── Full pipeline (build + test + E2E): 50%

Android Projects:
├── Debug APK: 25%
├── Debug + Unit: 35%
├── Release + E2E: 40%
```

### Step 4: Apply Realistic Correlations
```python
# Example correlation logic:

if project_type == 'react-native' and has_e2e_tests:
    memory_gb = random.uniform(8, 16)  # Heavy
    build_time_min = random.uniform(30, 90)
    
if project_type == 'python' and not has_integration_tests:
    memory_gb = random.uniform(0.5, 2)  # Light
    build_time_min = random.uniform(2, 8)

if cache_available == 0 and dependency_count > 100:
    build_time_min *= 2.5  # No cache = much slower

if is_release_build:
    build_time_min *= 1.5  # Optimization overhead
    cpu_avg_pct *= 1.2
```

---

## Proposed Changes

### [NEW] resources/generate_enhanced_dataset.py

Script to:
1. Read Kaggle `ci_cd_logs.csv` for pipeline patterns
2. Generate 1000+ records with realistic correlations
3. Output `enhanced_training_data.csv`

---

### [MODIFY] resources/train_model.py

Update to:
1. Use enhanced 30-column schema
2. Train model on new features
3. Output improved `model.pkl`

---

### [MODIFY] resources/predict.py

Update to:
1. Accept new features (project type, pipeline config, cache state)
2. Engineer derived features
3. Return predictions

---

### [NEW] src/org/ml/nodeselection/PipelineAnalyzer.groovy

New class to:
1. Detect project type from files (package.json, pom.xml, etc.)
2. Parse Jenkinsfile for stage types
3. Check cache state

---

### [MODIFY] vars/selectNode.groovy

Update to:
1. Call PipelineAnalyzer for context detection
2. Pass enhanced features to NodePredictor

---

## Verification Plan

### Automated Verification

```bash
# 1. Generate enhanced dataset
cd resources
python generate_enhanced_dataset.py
# Verify: 1000+ rows, 30 columns, reasonable distributions

# 2. Train model
python train_model.py \
  --data-path enhanced_training_data.csv \
  --model-path ../ml/
# Verify: R² > 0.75, no overfitting

# 3. Test predictions
echo '{"projectType":"react-native","hasE2ETests":true,"filesChanged":5}' > test.json
python predict.py --input test.json
# Verify: Should predict HIGH memory (8-16GB), LONG time (30-60min)
```

### Edge Case Validation

| Test Case | Expected |
|-----------|----------|
| React Native + E2E + No Cache | Memory: 8-16GB, Time: 40-90min |
| Python + Unit Only + Cache | Memory: 0.5-2GB, Time: 2-8min |
| Small change + Full Pipeline | Resources reflect PIPELINE, not change |
| First build (no cache) | 2-3x longer than cached |

---

## Summary

| Current | Enhanced |
|---------|----------|
| 60 records | 1000+ records |
| 14 columns | 30 columns |
| No project type | 6 project types |
| No pipeline awareness | Full stage detection |
| No cache awareness | Cache state included |
| Guessed correlations | Industry-researched values |
| ~40% accuracy | Target 75-85% accuracy |

---

## Next Steps

1. **Review this plan** - Any adjustments needed?
2. **Create `generate_enhanced_dataset.py`** - Generate realistic data
3. **Update training scripts** - Use new features
4. **Update Groovy code** - Detect pipeline context
5. **Test end-to-end** - Validate predictions make sense
