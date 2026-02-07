# ML Node Selector - Complete Edge Case Analysis

## Purpose
This document analyzes ALL scenarios and edge cases where build resource prediction can go wrong, and identifies the parameters needed for accurate prediction.

---

## Part 1: Edge Cases Where Current Model Fails

### Category 1: Code Change vs Pipeline Complexity Mismatch

| Edge Case | What Happens | Why Model Fails | Impact |
|-----------|--------------|-----------------|--------|
| **Small change, Full pipeline** | 1 line fix but runs full test suite | Model sees small change, predicts low resources | Under-provisioned, build fails |
| **Large change, Trivial pipeline** | 500 files renamed, only lint runs | Model sees big change, predicts high resources | Over-provisioned, wasted money |
| **No code change, Config only** | Only pipeline config changed | Model sees 0 code changes | Predicts minimal, but might trigger full rebuild |
| **Documentation only** | Only .md files changed | Model treats like code changes | Over-predicts resources |

### Category 2: Dependency-Related Edge Cases

| Edge Case | What Happens | Why Model Fails | Impact |
|-----------|--------------|-----------------|--------|
| **First build ever** | No cache, downloads 500+ packages | Model doesn't know cache state | Massively under-predicts time |
| **Dependency update** | `package-lock.json` changed | Model sees 1 file changed | Triggers full npm install (20+ min) |
| **Cache expired/cleared** | CI cache purged by admin | Model assumes cache exists | Under-predicts time |
| **New dependency added** | 1 line in requirements.txt | Could add 50+ transitive deps | Massively under-predicts |
| **Native dependencies** | numpy, tensorflow, etc. | Compilation time varies by machine | Unpredictable build time |

### Category 3: Build Type Edge Cases

| Edge Case | What Happens | Why Model Fails | Impact |
|-----------|--------------|-----------------|--------|
| **Debug vs Release** | Release has optimization, signing | Same code, 2x build time for release | Under-predicts release builds |
| **Clean build forced** | `--clean` flag or fresh workspace | Full rebuild vs incremental | 10x time difference |
| **Incremental build** | Only changed modules rebuild | Depends on build system intelligence | Varies wildly |
| **Multi-flavor build** | Android: debug, release, staging | Builds 3 APKs instead of 1 | 3x resources needed |

### Category 4: Testing Edge Cases

| Edge Case | What Happens | Why Model Fails | Impact |
|-----------|--------------|-----------------|--------|
| **Unit tests only** | Fast, in-memory tests | 2-5 minutes | Predictable |
| **Integration tests** | Database, API calls | 10-20 minutes, needs services | Medium complexity |
| **E2E tests (Appium)** | Real emulator/device | 30-60 minutes, 8GB+ RAM | Very heavy, unpredictable |
| **Visual regression** | Screenshot comparisons | GPU-intensive | Needs specific instance type |
| **Load testing** | Simulates 1000+ users | CPU-intensive | Needs large instance |
| **Flaky tests + retries** | Test fails, retries 3x | 3x expected time | Unpredictable |

### Category 5: Infrastructure Edge Cases

| Edge Case | What Happens | Why Model Fails | Impact |
|-----------|--------------|-----------------|--------|
| **Network slowdown** | Package registry slow/down | Download time: 1 min → 30 min | Unpredictable |
| **Shared CI overload** | 50 builds running | CPU contention | 2-5x slower |
| **Disk space issues** | Agent disk nearly full | Build fails or slows | Random failures |
| **Agent warm vs cold** | Docker images cached or not | Docker pull: 30s vs 10min | Unpredictable |
| **Different agent specs** | Mixed fleet (4GB vs 16GB agents) | Same label, different performance | Inconsistent times |

### Category 6: Project-Specific Edge Cases

| Edge Case | What Happens | Why Model Fails | Impact |
|-----------|--------------|-----------------|--------|
| **Monorepo** | 100 projects in one repo | 1 change can trigger 10 builds | Cascading resource needs |
| **Microservices** | Change in shared lib | Triggers rebuilds in 20 services | Exponential impact |
| **Generated code** | Protobuf, GraphQL codegen | Small schema change → 1000s of files | Unpredictable |
| **Asset-heavy project** | Large images/videos | Network transfer time | Bandwidth-dependent |

### Category 7: External Service Edge Cases

| Edge Case | What Happens | Why Model Fails | Impact |
|-----------|--------------|-----------------|--------|
| **Database provisioning** | Test needs fresh DB | 2-5 min to spin up | Adds hidden time |
| **Docker container start** | Services in docker-compose | 5+ containers to start | Adds hidden time |
| **Cloud API calls** | Deploy to AWS/GCP | API rate limits | Unpredictable delays |
| **Artifact upload** | Large APK to Nexus/S3 | Network-dependent | 1-10 min variable |

---

## Part 2: Complete Feature Set for Accurate Prediction

### Tier 1: Code Change Features (Current - 30% accuracy contribution)

```
┌──────────────────────────────────────────────────────────────┐
│ Feature               │ Type    │ How to Get               │
├──────────────────────────────────────────────────────────────┤
│ files_changed         │ int     │ git diff --name-only     │
│ lines_added           │ int     │ git diff --numstat       │
│ lines_deleted         │ int     │ git diff --numstat       │
│ net_lines             │ int     │ added - deleted          │
│ total_changes         │ int     │ added + deleted          │
│ code_density          │ float   │ changes / files          │
└──────────────────────────────────────────────────────────────┘
```

### Tier 2: File Type Features (NEW - 15% accuracy contribution)

```
┌──────────────────────────────────────────────────────────────┐
│ Feature                │ Type    │ Why It Matters           │
├──────────────────────────────────────────────────────────────┤
│ source_files_changed   │ int     │ .java, .py, .js changes  │
│ test_files_changed     │ int     │ Changes in test/ folder  │
│ config_files_changed   │ int     │ .yml, .json, .xml        │
│ docs_only              │ bool    │ Only .md files = fast    │
│ deps_file_changed      │ bool    │ package.json, pom.xml    │
│ dockerfile_changed     │ bool    │ Triggers image rebuild   │
│ pipeline_file_changed  │ bool    │ Jenkinsfile changed      │
└──────────────────────────────────────────────────────────────┘
```

### Tier 3: Project Context Features (NEW - 20% accuracy contribution)

```
┌──────────────────────────────────────────────────────────────┐
│ Feature               │ Type    │ How to Get                │
├──────────────────────────────────────────────────────────────┤
│ project_type          │ enum    │ Detect from files:        │
│                       │         │ 0=python, 1=java,         │
│                       │         │ 2=node, 3=react-native,   │
│                       │         │ 4=android, 5=ios          │
│ repo_size_mb          │ float   │ du -sh .git               │
│ total_files_count     │ int     │ find . -type f | wc -l    │
│ dependency_count      │ int     │ Parse package.json/pom    │
│ is_monorepo           │ bool    │ Multiple package.json?    │
│ has_native_deps       │ bool    │ C/C++ compilation needed  │
└──────────────────────────────────────────────────────────────┘
```

### Tier 4: Pipeline Structure Features (NEW - 25% accuracy contribution)

```
┌──────────────────────────────────────────────────────────────┐
│ Feature               │ Type    │ Why It Matters            │
├──────────────────────────────────────────────────────────────┤
│ stages_count          │ int     │ More stages = more time   │
│ has_build_stage       │ bool    │ Compilation stage?        │
│ has_test_stage        │ bool    │ Testing stage?            │
│ has_deploy_stage      │ bool    │ Deployment stage?         │
│ has_parallel_stages   │ bool    │ Parallel = more memory    │
│ parallel_branch_count │ int     │ How many parallel?        │
│                       │         │                           │
│ == Test Types ==      │         │                           │
│ has_unit_tests        │ bool    │ Fast tests                │
│ has_integration_tests │ bool    │ Medium tests              │
│ has_e2e_tests         │ bool    │ Slow tests (Appium)       │
│ has_performance_tests │ bool    │ CPU-intensive             │
│ test_file_count       │ int     │ Proxy for test duration   │
│                       │         │                           │
│ == Special Stages ==  │         │                           │
│ has_docker_build      │ bool    │ Docker image creation     │
│ uses_emulator         │ bool    │ Android/iOS emulator      │
│ uses_database         │ bool    │ DB provisioning time      │
│ has_artifact_publish  │ bool    │ Upload to Nexus/S3        │
└──────────────────────────────────────────────────────────────┘
```

### Tier 5: Cache State Features (NEW - 20% accuracy contribution)

```
┌──────────────────────────────────────────────────────────────┐
│ Feature                │ Type    │ How to Get               │
├──────────────────────────────────────────────────────────────┤
│ is_first_build         │ bool    │ No previous builds?      │
│ has_dependency_cache   │ bool    │ node_modules/.m2 exists? │
│ cache_age_hours        │ float   │ How old is cache?        │
│ deps_changed_since_cache│ bool   │ Lock file changed?       │
│ has_docker_cache       │ bool    │ Docker layers cached?    │
│ workspace_clean        │ bool    │ Fresh workspace?         │
│ previous_build_failed  │ bool    │ Might need clean build   │
└──────────────────────────────────────────────────────────────┘
```

### Tier 6: Historical Features (NEW - 30% accuracy contribution)

```
┌──────────────────────────────────────────────────────────────┐
│ Feature                   │ Type  │ Data Source             │
├──────────────────────────────────────────────────────────────┤
│ avg_build_time_last_5     │ float │ Jenkins API             │
│ avg_build_time_last_20    │ float │ Jenkins API             │
│ max_build_time_last_5     │ float │ For worst case          │
│ min_build_time_last_5     │ float │ For best case           │
│ build_time_std_dev        │ float │ How variable?           │
│                           │       │                         │
│ avg_memory_last_5         │ float │ Monitoring data         │
│ max_memory_last_5         │ float │ Peak usage              │
│ avg_cpu_last_5            │ float │ Monitoring data         │
│                           │       │                         │
│ success_rate_last_10      │ float │ Flaky builds?           │
│ retry_rate                │ float │ How often retries?      │
│                           │       │                         │
│ == Per-Stage History ==   │       │                         │
│ avg_install_time          │ float │ npm/pip install phase   │
│ avg_build_time            │ float │ Compilation phase       │
│ avg_test_time             │ float │ Test execution phase    │
└──────────────────────────────────────────────────────────────┘
```

### Tier 7: Infrastructure Features (NEW - 10% accuracy contribution)

```
┌──────────────────────────────────────────────────────────────┐
│ Feature               │ Type    │ How to Get                │
├──────────────────────────────────────────────────────────────┤
│ time_of_day_hour      │ int     │ 0-23, peak vs off-peak    │
│ day_of_week           │ int     │ 0-6, weekday vs weekend   │
│ current_queue_depth   │ int     │ Jenkins queue length      │
│ active_builds_count   │ int     │ Concurrent builds         │
│ target_agent_label    │ string  │ Which agent pool?         │
│ agent_cpu_cores       │ int     │ Agent spec                │
│ agent_memory_gb       │ int     │ Agent spec                │
└──────────────────────────────────────────────────────────────┘
```

### Tier 8: Build Configuration Features (NEW - 10% accuracy contribution)

```
┌──────────────────────────────────────────────────────────────┐
│ Feature               │ Type    │ Why It Matters            │
├──────────────────────────────────────────────────────────────┤
│ build_type            │ enum    │ debug/release/staging     │
│ is_clean_build        │ bool    │ --clean flag used?        │
│ is_full_build         │ bool    │ Full vs incremental       │
│ target_platforms      │ int     │ Android+iOS = 2x          │
│ build_variants        │ int     │ Debug+Release = 2x        │
│ optimization_level    │ enum    │ Higher = slower           │
│ code_signing_enabled  │ bool    │ iOS signing = slow        │
└──────────────────────────────────────────────────────────────┘
```

---

## Part 3: Questions to Answer for Your POC

### About Your Projects

1. **What types of projects will use this?**
   - [ ] React Native mobile apps
   - [ ] Android native (Java/Kotlin)
   - [ ] iOS native (Swift)
   - [ ] Python backend
   - [ ] Java/Spring backend
   - [ ] Node.js backend
   - [ ] Other: _______________

2. **What testing types do you run?**
   - [ ] Unit tests (Jest, JUnit, pytest)
   - [ ] Integration tests
   - [ ] E2E tests (Appium, Selenium, Cypress)
   - [ ] Performance/Load tests
   - [ ] Visual regression tests

3. **What are your heaviest pipeline stages?**
   - [ ] Dependency installation (npm/pip/maven)
   - [ ] Compilation/Build
   - [ ] Testing
   - [ ] Docker image build
   - [ ] Deployment
   - [ ] Other: _______________

### About Your Infrastructure

4. **Do you use caching?**
   - [ ] Yes, CI-level cache (GitHub Actions cache, Jenkins cache plugin)
   - [ ] Yes, shared NFS/EFS for node_modules/.m2
   - [ ] No caching, fresh every time
   - [ ] Inconsistent (sometimes cached)

5. **What's your Jenkins agent setup?**
   - [ ] Static agents (always running)
   - [ ] Dynamic agents (EC2 plugin, Kubernetes)
   - [ ] Mixed (some static, some dynamic)
   - [ ] Agent instance types vary

6. **Do you have historical build data?**
   - [ ] Yes, in Jenkins (can extract)
   - [ ] Yes, in external monitoring (Datadog, Prometheus)
   - [ ] No historical data available
   - [ ] Limited data (< 100 builds)

### About Accuracy Requirements

7. **What's acceptable prediction accuracy?**
   - [ ] Within 20% of actual (most apps)
   - [ ] Within 10% of actual (cost-critical)
   - [ ] Order of magnitude correct (POC level)

8. **What's more important to avoid?**
   - [ ] Under-provisioning (build fails) ← Usually this
   - [ ] Over-provisioning (wasted money)

---

## Part 4: Recommended Feature Set by Complexity

### Level 1: Quick POC (Your Current + Minor Additions)

```python
features = [
    # Current (7)
    'files_changed', 'lines_added', 'lines_deleted',
    'deps_changed', 'is_main', 'is_release', 'code_density',
    
    # Quick adds (5) - Easy to implement
    'project_type',         # Detect from files
    'has_tests_folder',     # fileExists('test/')
    'has_e2e_folder',       # fileExists('e2e/')
    'stages_count',         # Count from Jenkinsfile
    'is_first_build',       # Check previous builds
]
# Total: 12 features
# Accuracy: ~50-60%
# Effort: 1-2 days
```

### Level 2: Better POC (Add Pipeline Analysis)

```python
features = [
    # Level 1 (12) +
    
    # Pipeline analysis (8)
    'has_docker_build',
    'has_artifact_publish', 
    'uses_emulator',
    'parallel_stages',
    'has_integration_tests',
    'dependency_count',
    'test_file_count',
    'build_variants',
]
# Total: 20 features
# Accuracy: ~65-75%
# Effort: 1 week
```

### Level 3: Production Ready (Add Historical Data)

```python
features = [
    # Level 2 (20) +
    
    # Historical (10)
    'avg_build_time_last_5',
    'avg_memory_last_5',
    'max_build_time_last_5',
    'success_rate_last_10',
    'has_dependency_cache',
    'cache_age_hours',
    'avg_install_time',
    'avg_test_time',
    'previous_build_failed',
    'time_of_day_hour',
]
# Total: 30 features
# Accuracy: ~80-90%
# Effort: 2-3 weeks + data collection period
```

---

## Part 5: Data Collection Strategy

### Phase 1: Collect Real Data (2-4 weeks)

```groovy
// Add to every Jenkinsfile
post {
    always {
        script {
            // Collect actual metrics
            def actualMetrics = [
                build_id: env.BUILD_ID,
                project: env.JOB_NAME,
                duration_sec: currentBuild.duration / 1000,
                result: currentBuild.result,
                
                // Per-stage times (if instrumented)
                install_time: env.STAGE_INSTALL_TIME,
                build_time: env.STAGE_BUILD_TIME,
                test_time: env.STAGE_TEST_TIME,
                
                // Resource usage (if monitored)
                peak_memory_mb: env.PEAK_MEMORY,
                avg_cpu_percent: env.AVG_CPU,
                
                // Git metrics
                files_changed: env.GIT_FILES_CHANGED,
                lines_added: env.GIT_LINES_ADDED,
                
                // Context
                branch: env.BRANCH_NAME,
                build_type: params.BUILD_TYPE,
                cache_hit: env.CACHE_HIT,
                
                // Predicted vs Actual
                predicted_time: env.ML_PREDICTED_TIME,
                predicted_memory: env.ML_PREDICTED_MEMORY,
            ]
            
            // Send to database/CSV
            writeJSON file: 'build_metrics.json', json: actualMetrics
            archiveArtifacts 'build_metrics.json'
        }
    }
}
```

### Phase 2: Analyze Collected Data

```python
# After 2-4 weeks, analyze:

import pandas as pd

df = pd.read_csv('collected_builds.csv')

# Find what actually matters
correlation = df.corr()['actual_build_time']
print("Top correlating features:")
print(correlation.sort_values(ascending=False)[:10])

# Find edge cases
slow_builds = df[df['actual_build_time'] > df['actual_build_time'].quantile(0.9)]
print("What makes builds slow?")
print(slow_builds.describe())
```

### Phase 3: Retrain Model with Real Data

```python
# Train on YOUR actual data, not synthetic
model = RandomForestRegressor(n_estimators=200)
model.fit(X_real, y_real)

# Much more accurate for YOUR projects!
```

---

## Summary: Decision Matrix

| Approach | Accuracy | Effort | Best For |
|----------|----------|--------|----------|
| **Current Model** | 30-40% | Done | Demo only |
| **+ Pipeline Analysis** | 50-60% | 2-3 days | Quick POC |
| **+ Project Profiles** | 60-70% | 1 week | Known projects |
| **+ Historical Learning** | 80-90% | 3-4 weeks | Production |
| **+ Real-time Infra Data** | 90%+ | 6-8 weeks | Enterprise |

---

## Next Steps

Please answer the questions in Part 3, and I'll help you:
1. Design the right feature set for your needs
2. Create the updated training data schema
3. Implement the enhanced prediction model
