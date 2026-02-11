/**
 * ML-Based AWS Node Selector Pipeline
 * =====================================
 * 
 * Stage 1: Collect Metadata
 *   - Analyzes git changes (files, lines, deps, branch)
 *   - Detects project type (Java, Python, Node, etc.)
 *   - Counts dependencies from pom.xml / package.json / requirements.txt
 *   - Detects pipeline features (E2E, Docker, Emulator, etc.)
 *   - Checks cache state (first build, cache available)
 * 
 * Stage 2: ML Prediction & Node Selection
 *   - Feeds 27 features to Random Forest ML model
 *   - Predicts CPU, Memory, and Build Time
 *   - Selects the best AWS EC2 node for this build
 *   - Shows all available nodes for comparison
 */
@Library('ML-ANS-EC2-Node-Selector') _

pipeline {
    agent any

    parameters {
        choice(
            name: 'BUILD_TYPE',
            choices: ['debug', 'release'],
            description: 'Type of build to predict resources for'
        )
    }

    stages {

        // ═══════════════════════════════════════════════════════════
        // STAGE 1: Collect Metadata
        // Gathers all information about the project and code changes
        // ═══════════════════════════════════════════════════════════
        stage('Collect Metadata') {
            steps {
                checkout scm

                // ============ WINDOWS ============
                bat 'python --version'

                // ============ UBUNTU/LINUX (commented) ============
                // sh 'python3 --version'

                script {
                    // Collect git metrics + pipeline analysis
                    env.METADATA_JSON = ''
                    def metadata = collectMetadata(
                        buildType: params.BUILD_TYPE
                    )

                    // Store metadata as JSON for Stage 2
                    env.METADATA_JSON = groovy.json.JsonOutput.toJson(metadata)
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 2: ML Prediction & Node Selection
        // Feeds metadata into ML model and selects optimal AWS node
        // ═══════════════════════════════════════════════════════════
        stage('ML Prediction & Node Selection') {
            steps {
                script {
                    // Parse metadata from Stage 1
                    def metadata = new groovy.json.JsonSlurper().parseText(env.METADATA_JSON)

                    // Run ML prediction and select best node
                    def result = mlPredict(
                        metadata: metadata
                    )

                    // Store result for reference
                    echo "\n════════════════════════════════════════"
                    echo "  FINAL RESULT: Use '${result.label}' (${result.instanceType})"
                    echo "════════════════════════════════════════"
                }
            }
        }
    }

    post {
        success {
            echo '✅ ML Node Selection Pipeline completed successfully!'
        }
        failure {
            echo '❌ ML Node Selection Pipeline failed!'
        }
    }
}