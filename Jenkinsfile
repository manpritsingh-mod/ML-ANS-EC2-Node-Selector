/**
 * Combined Jenkinsfile: ML Node Selector + UnifiedCI Template
 * 
 * FLOW:
 * 1. ML Node Selector analyzes code & pipeline → predicts CPU/Memory/Time
 * 2. Selects optimal AWS EC2 node (aws-small / aws-medium / aws-large / aws-xlarge)
 * 3. UnifiedCI template runs the actual build/test/deploy on that node
 *
 * LIBRARIES:
 * - ML-ANS-EC2-Node-Selector: ML-based node selection
 * - My_UnifiedCI: Build/test/deploy templates
 */
@Library(['ML-ANS-EC2-Node-Selector', 'My_UnifiedCI']) _

pipeline {
    agent none

    parameters {
        choice(
            name: 'BUILD_TYPE',
            choices: ['debug', 'release'],
            description: 'Type of build'
        )
    }

    // tools {
    //     maven 'Maven 3.8.1'
    //     gradle 'Gradle 7.5'
    //     allure 'Allure-2.34.1'
    // }

    environment {
        PROJECT_LANGUAGE = ''
        BUILD_TOOL = ''
        RUN_UNIT_TESTS = ''
        RUN_LINT_TESTS = ''
    }

    stages {

        // ═══════════════════════════════════════════════════════════
        // STAGE 1: ML Node Selection
        // Analyzes git changes + pipeline to predict optimal node
        // ═══════════════════════════════════════════════════════════
        stage('ML Node Selection') {
            agent any

            steps {
                checkout scm

                // ============ WINDOWS ============
                bat 'python --version'
                
                // ============ UBUNTU/LINUX (commented) ============
                // sh 'python3 --version'

                script {
                    logger.info("═══ STAGE: ML NODE SELECTION ═══")
                    
                    def prediction = selectNode(
                        buildType: params.BUILD_TYPE
                    )

                    // Store predictions for next stage
                    env.SELECTED_LABEL = prediction.label
                    env.SELECTED_INSTANCE = prediction.instanceType
                    env.PREDICTED_MEMORY = prediction.predictedMemoryGb.toString()
                    env.PREDICTED_CPU = prediction.predictedCpu.toString()
                    env.PREDICTED_TIME = prediction.predictedTimeMinutes.toString()
                    env.ML_PROJECT_TYPE = prediction.projectType ?: 'unknown'

                    logger.info("ML Result → Node: ${env.SELECTED_LABEL} (${env.SELECTED_INSTANCE})")
                    logger.info("ML Result → Memory: ${env.PREDICTED_MEMORY} GB, CPU: ${env.PREDICTED_CPU}%")
                    logger.info("ML Result → Estimated Time: ${env.PREDICTED_TIME} min")
                    logger.info("ML Result → Project Type: ${env.ML_PROJECT_TYPE}")
                }
            }

            post {
                always {
                    echo "🏷️ Build will run on: ${env.SELECTED_LABEL} (${env.SELECTED_INSTANCE})"
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 2: Build/Test/Deploy on ML-Selected Node
        // Uses UnifiedCI templates on the optimal node
        // ═══════════════════════════════════════════════════════════
        stage('Setup and Execution') {
            agent { label env.SELECTED_LABEL }

            steps {
                checkout scm

                script {
                    logger.info("═══ STAGE: SETUP AND EXECUTION ═══")
                    logger.info("Running on ML-selected node: ${env.SELECTED_LABEL}")
                    logger.info("Instance Type: ${env.SELECTED_INSTANCE}")

                    // Read project configuration from YAML
                    def config = core_utils.readProjectConfig()
                    logger.info("Config map content: ${config}")

                    if (config && !config.isEmpty()) {
                        // Setup global environment
                        core_utils.setupEnvironment()
                        logger.info("Global environment setup completed")

                        // Call appropriate template based on the project language
                        logger.info("Calling template for: ${config.project_language}")
                        switch (config.project_language) {
                            case 'java-maven':
                                logger.info("Executing Java Maven template")
                                javaMaven_template(config)
                                break
                            case 'java-gradle':
                                logger.info("Executing Java Gradle template")
                                javaGradle_template(config)
                                break
                            case 'python':
                                logger.info("Executing Python template")
                                python_template(config)
                                break
                            default:
                                error("Unsupported project language: ${config.project_language}")
                        }

                        logger.info("Project template execution completed")

                    } else {
                        error("PROJECT_CONFIG is empty or missing")
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 3: ML Accuracy Comparison (Optional)
        // Compare predicted vs actual resource usage
        // ═══════════════════════════════════════════════════════════
        stage('ML Accuracy Report') {
            agent { label env.SELECTED_LABEL }

            steps {
                script {
                    logger.info("═══ STAGE: ML ACCURACY REPORT ═══")

                    def actualTimeMin = (currentBuild.duration / 60000).round(1)

                    echo "╔══════════════════════════════════════════════════╗"
                    echo "║         ML PREDICTION vs ACTUAL RESULTS         ║"
                    echo "╠══════════════════════════════════════════════════╣"
                    echo "║  Metric          │ Predicted    │ Actual        ║"
                    echo "╠══════════════════════════════════════════════════╣"
                    echo "║  Memory (GB)     │ ${env.PREDICTED_MEMORY.padRight(13)}│ (monitor)     ║"
                    echo "║  CPU (%)         │ ${env.PREDICTED_CPU.padRight(13)}│ (monitor)     ║"
                    echo "║  Time (min)      │ ${env.PREDICTED_TIME.padRight(13)}│ ${actualTimeMin.toString().padRight(14)}║"
                    echo "║  Node            │ ${env.SELECTED_LABEL.padRight(13)}│ ✅ Used       ║"
                    echo "║  Instance        │ ${env.SELECTED_INSTANCE.padRight(13)}│ ✅ Used       ║"
                    echo "╚══════════════════════════════════════════════════╝"

                    // Calculate time accuracy
                    def predictedTime = env.PREDICTED_TIME.toDouble()
                    def timeError = Math.abs(predictedTime - actualTimeMin)
                    def timeAccuracy = Math.max(0, 100 - (timeError / predictedTime * 100)).round(1)

                    logger.info("Time Prediction Accuracy: ${timeAccuracy}%")
                    logger.info("Time Error: ${timeError.round(1)} minutes")
                }
            }
        }
    }

    post {
        always {
            script {
                logger.info("=== SENDING NOTIFICATIONS ===")

                // Send notification with ML info
                def buildStatus = currentBuild.result ?: 'SUCCESS'
                def config = [
                    notifications: [
                        email: [recipients: ["smanprit022@gmail.com"]]
                    ]
                ]

                notify.notifyBuildStatus(buildStatus, config)
                logger.info("Notification sent successfully")
            }
        }

        success {
            script {
                logger.info("BUILD SUCCESSFUL on ${env.SELECTED_LABEL}!")
            }
        }

        failure {
            script {
                logger.error("BUILD FAILED on ${env.SELECTED_LABEL}!")
            }
        }

        unstable {
            script {
                logger.warning("BUILD UNSTABLE on ${env.SELECTED_LABEL}!")
            }
        }
    }
}