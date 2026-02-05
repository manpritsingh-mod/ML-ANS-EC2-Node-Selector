# ML-ANS-EC2-Node-Selector

> **Intelligent Jenkins Agent Selection using Machine Learning**

A Jenkins Shared Library that uses Random Forest ML to predict build resource requirements and automatically select the optimal AWS EC2 instance for CI/CD pipelines.

[![Jenkins](https://img.shields.io/badge/Jenkins-2.x-red?logo=jenkins)](https://www.jenkins.io/)
[![Python](https://img.shields.io/badge/Python-3.10+-blue?logo=python)](https://www.python.org/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

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

This library analyzes git commit metrics and uses ML to predict:
- **CPU Usage** (%)
- **Memory Requirements** (GB)
- **Build Duration** (minutes)

Then automatically selects the appropriate AWS EC2 instance type.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         JENKINS PIPELINE                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────────┐  │
│  │ Git Commit   │───▶│ GitAnalyzer  │───▶│ Feature Engineering  │  │
│  │ (PR/Push)    │    │ (Groovy)     │    │ (9 features)         │  │
│  └──────────────┘    └──────────────┘    └──────────┬───────────┘  │
│                                                      │              │
│                                                      ▼              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────────┐  │
│  │ AWS EC2      │◀───│ LabelMapper  │◀───│ Random Forest Model  │  │
│  │ Agent        │    │ (Groovy)     │    │ (Python/sklearn)     │  │
│  └──────────────┘    └──────────────┘    └──────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Features

| Feature | Description |
|---------|-------------|
| 🤖 **ML-Powered Prediction** | Random Forest model trained on historical build data |
| 📊 **Git Metrics Analysis** | Automatically extracts files changed, lines added/deleted |
| 🏷️ **Dynamic Label Selection** | Maps predictions to Jenkins agent labels |
| ☁️ **AWS EC2 Integration** | Selects optimal instance type (T3a Small → 2X Large) |
| 🔄 **Platform Agnostic** | Supports both Ubuntu/Linux and Windows agents |
| 📈 **20% Safety Buffer** | Adds buffer to predictions for reliability |

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
| Project Repository | `https://github.com/your-org/ML-ANS-EC2-Node-Selector.git` |

### 2. Prepare ML Model

```bash
# Navigate to resources directory
cd resources/

# Install dependencies
pip install -r requirements.txt

# Train model with your data
python train_model.py \
  --data-path sample_training_dataset.csv \
  --model-path ../ml/
```

### 3. Configure Agent Labels

Ensure your Jenkins agents have labels matching the `LabelMapper`:

| Label | Instance Type | Memory |
|-------|---------------|--------|
| `lightweight` | T3a Small | 1 GB |
| `executor` | T3a Small | 2 GB |
| `build` | T3a Large | 8 GB |
| `test` | T3a X Large | 16 GB |
| `heavytest` | T3a 2X Large | 32 GB |

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

### Return Object

```groovy
[
    label: 'build',                    // Jenkins agent label
    instanceType: 'T3a Large',         // AWS EC2 instance type
    predictedMemoryGb: 4.5,            // Predicted memory in GB
    predictedCpu: 45.2,                // Predicted CPU usage %
    predictedTimeMinutes: 12.5,        // Predicted build time
    confidence: 0.85,                  // Model confidence
    gitMetrics: [                      // Analyzed git metrics
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

---

## Project Structure

```
ML-ANS-EC2-Node-Selector/
├── vars/
│   └── selectNode.groovy          # Main pipeline step
├── src/org/ml/nodeselection/
│   ├── GitAnalyzer.groovy         # Git metrics extraction
│   ├── NodePredictor.groovy       # ML model integration
│   └── LabelMapper.groovy         # Label mapping logic
├── resources/
│   ├── train_model.py             # Model training script
│   ├── predict.py                 # Prediction script
│   ├── requirements.txt           # Python dependencies
│   └── sample_training_dataset.csv
├── ml/
│   └── model.pkl                  # Trained model (generated)
├── docs/
│   └── RANDOM_FOREST_EXPLAINED.md # How ML works
├── Jenkinsfile                    # Example pipeline
└── README.md
```

---

## How It Works

### 1. Git Analysis
```groovy
// GitAnalyzer extracts metrics from the commit
def metrics = [
    filesChanged: 15,      // git diff --name-only HEAD~1 | wc -l
    linesAdded: 350,       // git diff --numstat (column 1)
    linesDeleted: 120,     // git diff --numstat (column 2)
    depsChanged: 1,        // Files like pom.xml, package.json
    branch: 'main'
]
```

### 2. Feature Engineering
```python
# predict.py transforms raw metrics into ML features
features = {
    'files_changed': 15,
    'lines_added': 350,
    'lines_deleted': 120,
    'net_lines': 230,              # added - deleted
    'total_changes': 470,          # added + deleted
    'deps_changed': 1,
    'is_main': 1,                  # branch in ['main', 'master']
    'is_release': 0,               # buildType == 'release'
    'code_density': 31.3           # total_changes / files_changed
}
```

### 3. Random Forest Prediction
```
100 Decision Trees → Average Predictions → Final Output

Output: {cpu: 45.2, memoryGb: 4.5, timeMinutes: 12.5}
```

### 4. Label Mapping
```groovy
// LabelMapper selects instance based on memory (+ 20% buffer)
4.5 GB * 1.2 = 5.4 GB → Needs 8 GB → Label: 'build' → T3a Large
```

---

## API Reference

### selectNode.groovy

```groovy
/**
 * Main entry point for ML-based node selection
 * 
 * @param config Map with optional keys: buildType, modelPath
 * @return Map with label, instanceType, predictions, gitMetrics
 */
def call(Map config = [:])
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
| `ML model not found` | Model not trained | Run `train_model.py` |
| `python3: command not found` | Python not installed | Install Python 3.10+ |
| `git diff` returns empty | First commit / shallow clone | Use `git fetch --unshallow` |
| Pipeline hangs at pip install | Network/proxy issues | Add `--timeout 60` flag |

### Debug Mode

Add to your Jenkinsfile:
```groovy
script {
    def prediction = selectNode(buildType: 'debug')
    echo "Git Metrics: ${prediction.gitMetrics}"
    echo "Full Prediction: ${prediction}"
}
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

---

<p align="center">
  <b>Built with ❤️ for DevOps Engineers</b>
</p>
