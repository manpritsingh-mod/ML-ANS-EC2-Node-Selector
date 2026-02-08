# ML-ANS-EC2-Node-Selector

> **Intelligent Jenkins Agent Selection using Machine Learning**

A Jenkins Shared Library that uses Random Forest ML to predict build resource requirements and automatically select the optimal AWS EC2 instance for CI/CD pipelines.

[![Jenkins](https://img.shields.io/badge/Jenkins-2.x-red?logo=jenkins)](https://www.jenkins.io/)
[![Python](https://img.shields.io/badge/Python-3.10+-blue?logo=python)](https://www.python.org/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

---

## 🚀 What's New (v2.0)

| Feature | v1.0 | v2.0 |
|---------|------|------|
| **Features** | 9 (git metrics only) | **27 (full pipeline context)** |
| **Training Data** | 60 records | **1000+ records** |
| **Project Detection** | ❌ | ✅ Python/Java/Node/React Native/Android/iOS |
| **Pipeline Analysis** | ❌ | ✅ E2E tests, Docker, Emulator detection |
| **Cache Awareness** | ❌ | ✅ First build, cache state |
| **Model Accuracy** | ~40% | **~67% R²** |

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Usage](#usage)
- [Configuration](#configuration)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)
- [API Reference](#api-reference)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

---

## Overview

### Problem Statement

Traditional Jenkins pipelines use static agent labels, leading to:
- **Over-provisioning**: Heavy instances for small builds → wasted resources
- **Under-provisioning**: Light instances for heavy builds → failed builds
- **Manual tuning**: DevOps engineers guessing resource requirements

### Solution

This library analyzes **27 features** including git metrics, project type, and pipeline structure to predict:
- **CPU Usage** (%)
- **Memory Requirements** (GB)
- **Build Duration** (minutes)

Then automatically selects the appropriate AWS EC2 instance type.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           JENKINS PIPELINE                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐    ┌────────────────┐    ┌────────────────────────────┐   │
│  │ Git Commit   │───▶│ GitAnalyzer    │───▶│ Git Metrics                │   │
│  │ (PR/Push)    │    │ (Groovy)       │    │ (files, lines, deps)       │   │
│  └──────────────┘    └────────────────┘    └─────────────┬──────────────┘   │
│                                                           │                  │
│  ┌──────────────┐    ┌────────────────┐                  │                  │
│  │ Workspace    │───▶│ PipelineAnalyzer│───▶ ┌──────────────────────────┐   │
│  │ Analysis     │    │ (Groovy) [NEW] │    │ 27 Features Combined     │   │
│  └──────────────┘    └────────────────┘    └─────────────┬──────────────┘   │
│                                                           │                  │
│                                                           ▼                  │
│  ┌──────────────┐    ┌────────────────┐    ┌────────────────────────────┐   │
│  │ AWS EC2      │◀───│ LabelMapper    │◀───│ Random Forest Model        │   │
│  │ Agent        │    │ (Groovy)       │    │ (Python/sklearn)           │   │
│  └──────────────┘    └────────────────┘    └────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Features

### Core Capabilities

| Feature | Description |
|---------|-------------|
| 🤖 **ML-Powered Prediction** | Random Forest model trained on 1000+ records |
| 🔍 **Project Type Detection** | Auto-detects Python, Java, Node.js, React Native, Android, iOS |
| 📊 **Pipeline Analysis** | Detects E2E tests, Docker builds, emulator usage |
| 💾 **Cache Awareness** | Considers first build vs cached builds |
| 🏷️ **Dynamic Label Selection** | Maps predictions to Jenkins agent labels |
| ☁️ **AWS EC2 Integration** | Selects optimal instance type (T3 Small → 2X Large) |

### 27 Input Features (v2.0)

| Category | Features |
|----------|----------|
| **Project Context** | `project_type`, `repo_size_mb`, `is_monorepo` |
| **Git Metrics** | `files_changed`, `lines_added`, `lines_deleted`, `test_files_changed`, `deps_file_changed`, `dependency_count`, `source_files_pct` |
| **Pipeline Structure** | `stages_count`, `has_unit_tests`, `has_integration_tests`, `has_e2e_tests`, `has_docker_build`, `uses_emulator`, `has_deploy_stage`, `has_artifact_publish`, `parallel_stages`, `has_build_stage` |
| **Build Context** | `branch_type`, `build_type`, `environment` |
| **Cache State** | `is_first_build`, `cache_available`, `is_clean_build` |
| **Time Context** | `time_of_day_hour` |

---

## Prerequisites

### Jenkins Controller
- Jenkins 2.x or higher
- Pipeline plugin
- Git plugin

### Jenkins Agent (Ubuntu/Linux)
```bash
# Python 3.10+
python3 --version

# Git
git --version

# pip (for ML dependencies)
python3 -m pip --version
```

### Jenkins Agent (Windows)
```powershell
# Python 3.10+
python --version

# Git for Windows
git --version
```

---

## Installation

### 1. Add Shared Library to Jenkins

**Manage Jenkins** → **Configure System** → **Global Pipeline Libraries**

| Field | Value |
|-------|-------|
| Name | `ML-ANS-EC2-Node-Selector` |
| Default Version | `master` |
| Retrieval Method | Modern SCM |
| Source Code Management | Git |
| Project Repository | `https://github.com/manpritsingh-mod/ML-ANS-EC2-Node-Selector.git` |

### 2. Train ML Model (Optional - Pre-trained model included)

```bash
# Navigate to resources directory
cd resources/

# Install dependencies
pip install -r requirements.txt

# Generate enhanced training data
python generate_enhanced_dataset.py

# Train model with enhanced data
python train_model.py \
  --data-path training_features.csv \
  --model-path ../ml/
```

### 3. Configure Agent Labels

Ensure your Jenkins agents have labels matching the `LabelMapper`:

| Label | Instance Type | Memory | Use Case |
|-------|---------------|--------|----------|
| `aws-small` | T3.medium | 4 GB | Python, Node.js unit tests |
| `aws-medium` | T3.large | 8 GB | Java builds, Docker |
| `aws-large` | T3.xlarge | 16 GB | Android, iOS, E2E tests |
| `aws-xlarge` | T3.2xlarge | 32 GB | Heavy mobile builds with emulator |

---

## Usage

### Basic Usage

```groovy
@Library('ML-ANS-EC2-Node-Selector') _

pipeline {
    agent none

    stages {
        stage('ML Node Selection') {
            agent any
            steps {
                script {
                    def prediction = selectNode(
                        buildType: 'debug'
                    )
                    
                    echo "Selected Label: ${prediction.label}"
                    echo "Instance Type: ${prediction.instanceType}"
                    echo "Predicted Memory: ${prediction.predictedMemoryGb} GB"
                    echo "Project Type: ${prediction.projectType}"
                }
            }
        }

        stage('Build') {
            agent { label env.ML_SELECTED_LABEL }
            steps {
                sh 'make build'
            }
        }
    }
}
```

### With Parameters

```groovy
@Library('ML-ANS-EC2-Node-Selector') _

pipeline {
    agent none
    
    parameters {
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'])
    }

    stages {
        stage('ML Node Selection') {
            agent any
            steps {
                script {
                    def prediction = selectNode(
                        buildType: params.BUILD_TYPE,
                        modelPath: "${env.JENKINS_HOME}/ml-models"
                    )
                }
            }
        }
    }
}
```

---

## Configuration

### selectNode() Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `buildType` | String | `'debug'` | Build type: `debug` or `release` |
| `modelPath` | String | `${JENKINS_HOME}/ml-models` | Path to ML model |
| `useEnhancedAnalysis` | Boolean | `true` | Enable full pipeline analysis |

### Return Object

```groovy
[
    label: 'aws-large',               // Jenkins agent label
    instanceType: 't3.xlarge',        // AWS EC2 instance type
    predictedMemoryGb: 13.28,         // Predicted memory in GB
    predictedCpu: 87.3,               // Predicted CPU usage %
    predictedTimeMinutes: 106.1,      // Predicted build time
    confidence: 'medium',             // Prediction confidence
    projectType: 'react-native',      // Detected project type
    hasE2ETests: 1,                   // E2E tests detected
    usesEmulator: 1,                  // Emulator usage detected
    cacheAvailable: 1,                // Cache state
    gitMetrics: [                     // Analyzed git metrics
        filesChanged: 15,
        linesAdded: 350,
        linesDeleted: 120,
        depsChanged: 1,
        branch: 'feature/login'
    ]
]
```

### Environment Variables Set

| Variable | Description |
|----------|-------------|
| `ML_SELECTED_LABEL` | Selected Jenkins label |
| `ML_PREDICTED_MEMORY` | Predicted memory (GB) |
| `ML_PREDICTED_CPU` | Predicted CPU (%) |
| `ML_PREDICTED_TIME` | Predicted time (minutes) |
| `ML_PROJECT_TYPE` | Detected project type |
| `ML_HAS_E2E_TESTS` | E2E tests detected (0/1) |
| `ML_USES_EMULATOR` | Emulator detected (0/1) |
| `ML_PREDICTION_CONFIDENCE` | Prediction confidence level |

---

## Project Structure

```
ML-ANS-EC2-Node-Selector/
├── vars/
│   └── selectNode.groovy              # Main pipeline step (enhanced)
├── src/org/ml/nodeselection/
│   ├── GitAnalyzer.groovy             # Git metrics extraction
│   ├── PipelineAnalyzer.groovy        # Project/pipeline detection [NEW]
│   ├── NodePredictor.groovy           # ML model integration
│   └── LabelMapper.groovy             # Label mapping logic
├── ml/
│   ├── model.pkl                      # Trained model (27 features)
│   ├── predict.py                     # Enhanced prediction script
│   └── features.json                  # Feature metadata
├── resources/
│   ├── generate_enhanced_dataset.py   # Dataset generation [NEW]
│   ├── train_model.py                 # Enhanced training script
│   ├── predict.py                     # Prediction script (dev)
│   ├── requirements.txt               # Python dependencies
│   ├── enhanced_training_data.csv     # 1000+ training records [NEW]
│   ├── training_features.csv          # 27-feature dataset [NEW]
│   └── old_sample_training_dataset.csv # Original 60-row dataset
├── docs/
│   ├── FLOW_DOCUMENTATION.md          # Complete flow with diagrams [NEW]
│   ├── EDGE_CASE_ANALYSIS.md          # Edge cases analysis [NEW]
│   ├── ENHANCED_DATASET_IMPLEMENTATION_PLAN.md
│   └── RANDOM_FOREST_EXPLAINED.md     # How ML works
├── Jenkinsfile                        # Example pipeline
└── README.md
```

---

## How It Works

### Complete Flow

```
1. Pipeline calls selectNode()
        ↓
2. GitAnalyzer extracts git metrics
        ↓
3. PipelineAnalyzer detects:
   - Project type (package.json → Node.js/React Native)
   - Pipeline config (E2E, Docker, Emulator)
   - Cache state (first build, cache available)
        ↓
4. NodePredictor combines 27 features
        ↓
5. Python predict.py → model.pkl
        ↓
6. Random Forest returns [CPU, Memory, Time]
        ↓
7. LabelMapper → Jenkins label (aws-small/medium/large/xlarge)
        ↓
8. Pipeline runs on optimal node!
```

### Example Predictions

| Scenario | CPU | Memory | Time | Label |
|----------|-----|--------|------|-------|
| Python + Unit Tests | 53% | 3.2 GB | 17 min | aws-small |
| Java + Maven Build | 65% | 6.5 GB | 25 min | aws-medium |
| Android + Emulator E2E | 85% | 14 GB | 90 min | aws-large |
| React Native + Full E2E | 87% | 13 GB | 106 min | aws-large |

### Feature Importance (Top 10)

```
1. is_first_build         15.1%
2. project_type           13.7%
3. repo_size_mb            7.8%
4. uses_emulator           7.7%
5. cache_available         7.6%
6. is_clean_build          5.9%
7. dependency_count        4.1%
8. lines_deleted           3.7%
9. stages_count            3.6%
10. lines_added            3.3%
```

---

## API Reference

### selectNode.groovy

```groovy
/**
 * Main entry point for ML-based node selection
 * 
 * @param config Map with optional keys: buildType, modelPath, useEnhancedAnalysis
 * @return Map with label, instanceType, predictions, projectType, gitMetrics
 */
def call(Map config = [:])
```

### PipelineAnalyzer (NEW)

```groovy
/**
 * Analyzes workspace for project type and pipeline configuration
 * 
 * @param config Optional overrides (buildType)
 * @return Map with 27 features for ML prediction
 */
Map analyze(Map config = [:])
```

### GitAnalyzer

```groovy
/**
 * Analyzes git repository changes
 * 
 * @return Map with filesChanged, linesAdded, linesDeleted, 
 *         depsChanged, branch
 */
Map analyze()
```

### LabelMapper

```groovy
/**
 * Maps predicted memory to Jenkins label
 * 
 * @param predictedMemoryGb Predicted memory requirement
 * @return String Jenkins agent label
 */
String getLabel(double predictedMemoryGb)
```

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| `ML model not found` | Model not in workspace | Ensure `ml/model.pkl` exists |
| `python3: command not found` | Python not installed | Install Python 3.10+ |
| `git diff` returns empty | First commit / shallow clone | Use `git fetch --unshallow` |
| Low prediction accuracy | Using old model | Retrain with enhanced dataset |

### Debug Mode

Add to your Jenkinsfile:
```groovy
script {
    def prediction = selectNode(buildType: 'debug')
    echo "Project Type: ${prediction.projectType}"
    echo "Has E2E Tests: ${prediction.hasE2ETests}"
    echo "Uses Emulator: ${prediction.usesEmulator}"
    echo "Cache Available: ${prediction.cacheAvailable}"
    echo "Full Prediction: ${prediction}"
}
```

---

## Model Training

### Generate Enhanced Dataset

```bash
cd resources/
python generate_enhanced_dataset.py
```

This creates 1000+ realistic training records with all 27 features.

### Train Model

```bash
python train_model.py \
  --data-path training_features.csv \
  --model-path ../ml/
```

### Expected Output

```
Training Enhanced ML Model
==========================
Training samples: 800
Features: 27

Model Performance (Test Set):
  Overall R² Score:  0.67
  cpu_avg_pct     → R²: 0.63
  memory_gb       → R²: 0.79
  build_time_min  → R²: 0.59

✅ Model saved: ../ml/model.pkl
```

---

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- **scikit-learn** - Machine Learning library
- **Jenkins** - CI/CD automation platform
- **AWS EC2** - Cloud compute infrastructure
- **Kaggle AI-Driven CI/CD Pipeline Logs Dataset** - Training data patterns

---

<p align="center">
  <b>Built with ❤️ for DevOps Engineers</b>
</p>
