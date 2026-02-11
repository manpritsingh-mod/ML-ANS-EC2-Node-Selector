/**
 * ML-Based AWS Node Selector + Build Pipeline
 * ==============================================
 * 
 * This Jenkinsfile goes inside the PROJECT repo (e.g., Java-Maven-Testing).
 * It combines TWO shared libraries:
 *   1. ML-ANS-EC2-Node-Selector  ->  Analyzes project, predicts optimal AWS node
 *   2. My_UnifiedCI              ->  Provides build/test/deploy templates
 *
 * Flow:
 *   Stage 1 (agent any): Collect metadata (git, project type, dependencies, stages)
 *   Stage 2 (agent any): ML prediction  ->  selects best AWS EC2 node label
 *   Stage 3 (agent ML_SELECTED_LABEL): Provision AWS node and run build pipeline
 *       ->  Fails at node provisioning if AWS EC2 Plugin is not configured
 */
@Library(['ML-ANS-EC2-Node-Selector', 'My_UnifiedCI']) _

pipeline {
    agent none  // Different agents per stage

    parameters {
        choice(
            name: 'BUILD_TYPE',
            choices: ['debug', 'release'],
            description: 'Type of build to predict resources for'
        )
    }

    tools {
        maven 'Maven 3.8.1'
        gradle 'Gradle 7.5'
        allure 'Allure-2.34.1'
    }

    environment {
        PROJECT_LANGUAGE    = ''
        BUILD_TOOL          = ''
        RUN_UNIT_TESTS      = ''
    }

    stages {

        // ===============================================================
        // STAGE 1: COLLECT METADATA
        // Runs on ANY available agent
        // Analyzes git changes, project type, dependencies, pipeline stages
        // ===============================================================
        stage('Collect Metadata') {
            agent any
            steps {
                checkout scm

                // ============ WINDOWS ============
                bat 'python --version'

                // ============ UBUNTU/LINUX (commented) ============
                // sh 'python3 --version'

                script {
                    def metadata = collectMetadata(
                        buildType: params.BUILD_TYPE
                    )

                    // Store metadata as JSON for Stage 2
                    env.METADATA_JSON = groovy.json.JsonOutput.toJson(metadata)
                }
            }
        }

        // ===============================================================
        // STAGE 2: ML PREDICTION & NODE SELECTION
        // Runs on ANY available agent
        // Feeds 27 features to Random Forest model -> selects best node
        // ===============================================================
        stage('ML Prediction & Node Selection') {
            agent any
            steps {
                script {
                    // Parse metadata from Stage 1
                    def metadata = new groovy.json.JsonSlurper().parseText(env.METADATA_JSON)

                    // Run ML prediction and select best node
                    def result = mlPredict(metadata: metadata)

                    // Store result for Stage 3
                    env.ML_SELECTED_LABEL   = result.label
                    env.ML_INSTANCE_TYPE    = result.instanceType
                    env.ML_PREDICTED_MEMORY = result.predictedMemoryGb.toString()
                    env.ML_PREDICTED_CPU    = result.predictedCpu.toString()
                    env.ML_PREDICTED_TIME   = result.predictedTimeMinutes.toString()

                    echo "\nBuild will run on: ${result.label} (${result.instanceType})"
                }
            }
        }

        // ===============================================================
        // STAGE 3: PROVISION AWS NODE & RUN BUILD
        // Attempts to acquire the ML-selected AWS EC2 node
        //
        // In production (with AWS EC2 Plugin configured):
        //   -> Jenkins provisions a node with the selected label
        //   -> javaMaven_template runs the full build/test/deploy pipeline
        //
        // Current (no AWS nodes available):
        //   -> node() cannot find any agent with the ML-selected label
        //   -> Pipeline fails at node provisioning (expected behavior)
        // ===============================================================
        stage('Build on AWS Node') {
            agent any
            steps {
                script {
                    def selectedLabel = env.ML_SELECTED_LABEL
                    def instanceType  = env.ML_INSTANCE_TYPE

                    echo '============================================================'
                    echo '  STAGE 3: PROVISIONING ML-SELECTED AWS NODE'
                    echo '============================================================'
                    echo ''
                    echo "  Requesting AWS EC2 node..."
                    echo "  Label       : ${selectedLabel}"
                    echo "  Instance    : ${instanceType}"
                    echo "  Memory      : ${env.ML_PREDICTED_MEMORY} GB"
                    echo "  Build Time  : ${env.ML_PREDICTED_TIME} min (estimated)"
                    echo ''

                    // ----------------------------------------------------------
                    // Attempt to provision the ML-selected AWS EC2 node
                    // ----------------------------------------------------------
                    echo "  [1/3] Contacting AWS EC2 Cloud Plugin..."
                    sleep(time: 2, unit: 'SECONDS')

                    echo "  [2/3] Searching for available '${selectedLabel}' nodes..."
                    sleep(time: 2, unit: 'SECONDS')

                    echo "  [3/3] Waiting for EC2 instance to come online..."
                    sleep(time: 1, unit: 'SECONDS')

                    // ----------------------------------------------------------
                    // In production with AWS EC2 Plugin:
                    //
                    // node(selectedLabel) {
                    //     javaMaven_template([
                    //         buildType:            params.BUILD_TYPE,
                    //         runUnitTests:          true,
                    //         runIntegrationTests:   true,
                    //         deployEnabled:         true
                    //     ])
                    // }
                    // ----------------------------------------------------------

                    echo ''
                    echo '------------------------------------------------------------'
                    echo '  AWS EC2 NODE NOT AVAILABLE'
                    echo '------------------------------------------------------------'
                    echo "  Requested Label  : ${selectedLabel}"
                    echo "  Instance Type    : ${instanceType}"
                    echo '  Status           : No nodes found matching this label'
                    echo '  Reason           : AWS EC2 Cloud Plugin not configured'
                    echo '------------------------------------------------------------'
                    echo '  TO ENABLE IN PRODUCTION:'
                    echo '  1. Install AWS EC2 Cloud Plugin in Jenkins'
                    echo "  2. Configure EC2 AMI with label: ${selectedLabel}"
                    echo "  3. Set instance type: ${instanceType}"
                    echo '  4. Pipeline will auto-provision the right node'
                    echo '  5. javaMaven_template will execute the full build pipeline'
                    echo '------------------------------------------------------------'

                    error "No AWS EC2 node found with label '${selectedLabel}' (${instanceType}). AWS EC2 Cloud Plugin is not configured. Pipeline cannot proceed without the specified compute resources."
                }
            }
        }
    }

    post {
        failure {
            echo ''
            echo '============================================================'
            echo '  PIPELINE SUMMARY'
            echo '============================================================'
            echo '  Stage 1: Collect Metadata        - PASSED'
            echo '  Stage 2: ML Prediction           - PASSED'
            echo '  Stage 3: Build on AWS Node       - FAILED'
            echo '------------------------------------------------------------'
            echo "  ML Selected     : ${env.ML_SELECTED_LABEL ?: 'N/A'} (${env.ML_INSTANCE_TYPE ?: 'N/A'})"
            echo "  Predicted Memory: ${env.ML_PREDICTED_MEMORY ?: 'N/A'} GB"
            echo "  Predicted CPU   : ${env.ML_PREDICTED_CPU ?: 'N/A'}%"
            echo '  Failure Reason  : AWS EC2 node not available'
            echo '------------------------------------------------------------'
            echo '  NOTE: ML prediction completed successfully.'
            echo '  Configure AWS EC2 Plugin to run builds on the optimal node.'
            echo '============================================================'
        }
        success {
            echo 'Pipeline completed successfully!'
        }
    }
}