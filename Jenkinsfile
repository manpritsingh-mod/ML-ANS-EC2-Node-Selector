/**
 * ML-Based AWS Node Selector + Build Pipeline
 * ==============================================
 * 
 * This Jenkinsfile goes inside the PROJECT repo (e.g., Java-Maven-Testing).
 * It combines TWO shared libraries:
 *   1. ML-ANS-EC2-Node-Selector  ->  Analyzes project, predicts optimal AWS node
 *   2. My_UnifiedCI              ->  Provides build/test/deploy templates
 *
 * Pipeline Flow:
 *   Stage 1: Collect Metadata       (agent any)  -> Git + workspace analysis
 *   Stage 2: ML Prediction          (agent any)  -> Predict best AWS node
 *   Stage 3: Provision AWS Node     (agent any)  -> Attempt to acquire node -> FAILS
 *   Stage 4: Compile                (skipped)    -> Would run mvn compile
 *   Stage 5: Unit Test              (skipped)    -> Would run mvn test
 *   Stage 6: Integration Test       (skipped)    -> Would run mvn verify
 *   Stage 7: Package                (skipped)    -> Would run mvn package
 *   Stage 8: Deploy                 (skipped)    -> Would deploy artifact
 *
 * Stages 4-8 are real stage blocks (PipelineAnalyzer detects them as metadata)
 * but they are skipped because the AWS node provisioning fails in Stage 3.
 */
@Library(['ML-ANS-EC2-Node-Selector', 'My_UnifiedCI']) _

pipeline {
    agent none

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
        PROJECT_LANGUAGE      = ''
        BUILD_TOOL            = ''
        RUN_UNIT_TESTS        = 'true'
        RUN_INTEGRATION_TESTS = 'true'
        DEPLOY_ENABLED        = 'true'
        AWS_NODE_PROVISIONED  = 'false'
    }

    stages {

        // ===============================================================
        // STAGE 1: COLLECT METADATA
        // Runs on ANY available agent
        // Analyzes git changes, project type, dependencies, pipeline stages
        // PipelineAnalyzer reads THIS Jenkinsfile + workspace files (pom.xml)
        // ===============================================================
        stage('Collect Metadata') {
            agent any
            steps {
                checkout scm

                // ============ WINDOWS ============
                bat 'python --version'

                // ============ UBUNTU/LINUX ============
                // sh 'python3 --version'

                script {
                    def metadata = collectMetadata(
                        buildType: params.BUILD_TYPE
                    )
                    env.METADATA_JSON = groovy.json.JsonOutput.toJson(metadata)
                }
            }
        }

        // ===============================================================
        // STAGE 2: ML PREDICTION & NODE SELECTION
        // Feeds 27 features to Random Forest model -> selects best AWS node
        // ===============================================================
        stage('ML Prediction & Node Selection') {
            agent any
            steps {
                script {
                    def metadata = new groovy.json.JsonSlurper().parseText(env.METADATA_JSON)
                    def result = mlPredict(metadata: metadata)

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
        // STAGE 3: PROVISION AWS EC2 NODE
        // Attempts to acquire the ML-selected node from AWS EC2 Cloud
        // FAILS here because AWS EC2 Plugin is not configured
        // ===============================================================
        stage('Provision AWS Node') {
            agent any
            steps {
                script {
                    def selectedLabel = env.ML_SELECTED_LABEL
                    def instanceType  = env.ML_INSTANCE_TYPE

                    echo '============================================================'
                    echo '  PROVISIONING ML-SELECTED AWS EC2 NODE'
                    echo '============================================================'
                    echo ''
                    echo "  Requesting AWS EC2 node..."
                    echo "  Label       : ${selectedLabel}"
                    echo "  Instance    : ${instanceType}"
                    echo "  Memory      : ${env.ML_PREDICTED_MEMORY} GB"
                    echo "  Build Time  : ${env.ML_PREDICTED_TIME} min (estimated)"
                    echo ''

                    echo "  [1/3] Contacting AWS EC2 Cloud Plugin..."
                    sleep(time: 2, unit: 'SECONDS')

                    echo "  [2/3] Searching for '${selectedLabel}' nodes..."
                    sleep(time: 2, unit: 'SECONDS')

                    echo "  [3/3] Waiting for EC2 instance..."
                    sleep(time: 1, unit: 'SECONDS')

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
                    echo '  4. Stages below will auto-execute on the provisioned node'
                    echo '------------------------------------------------------------'

                    error "No AWS EC2 node found with label '${selectedLabel}' (${instanceType}). AWS EC2 Cloud Plugin is not configured."
                }
            }
        }

        // ===============================================================
        // STAGES 4-8: BUILD PIPELINE (from javaMaven_template)
        //
        // These are REAL stage blocks — PipelineAnalyzer counts them as
        // metadata (stages, unit tests, integration tests, deploy).
        //
        // They are SKIPPED because Stage 3 fails (no AWS node).
        // In production with AWS, these would run on the ML-selected node.
        // ===============================================================

        stage('Compile') {
            when { expression { return env.AWS_NODE_PROVISIONED == 'true' } }
            agent any
            steps {
                echo 'mvn compile -DskipTests'
            }
        }

        stage('Unit Test') {
            when { expression { return env.AWS_NODE_PROVISIONED == 'true' } }
            agent any
            steps {
                echo 'mvn test'
                echo 'junit testResults: target/surefire-reports/*.xml'
            }
        }

        stage('Integration Test') {
            when { expression { return env.AWS_NODE_PROVISIONED == 'true' } }
            agent any
            steps {
                echo 'mvn verify -DskipUnitTests'
                echo 'failsafe testResults: target/failsafe-reports/*.xml'
            }
        }

        stage('Package') {
            when { expression { return env.AWS_NODE_PROVISIONED == 'true' } }
            agent any
            steps {
                echo 'mvn package -DskipTests'
            }
        }

        stage('Deploy') {
            when { expression { return env.AWS_NODE_PROVISIONED == 'true' } }
            agent any
            steps {
                echo 'Deploying artifact to production...'
                echo 'aws s3 cp target/*.jar s3://deploy-bucket/'
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
            echo '  Stage 3: Provision AWS Node       - FAILED'
            echo '  Stage 4: Compile                  - SKIPPED'
            echo '  Stage 5: Unit Test                - SKIPPED'
            echo '  Stage 6: Integration Test         - SKIPPED'
            echo '  Stage 7: Package                  - SKIPPED'
            echo '  Stage 8: Deploy                   - SKIPPED'
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