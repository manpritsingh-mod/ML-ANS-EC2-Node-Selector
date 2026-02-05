@Library('ML-ANS-EC2-Node-Selector') _

pipeline {
    agent none

    parameters {
        choice(
            name: 'BUILD_TYPE',
            choices: ['debug', 'release'],
            description: 'Type of build'
        )
    }

    stages {
        stage('ML Node Selection') {
            agent any

            steps {
                checkout scm

                bat 'python --version'

                script {
                    def prediction = selectNode(
                        buildType: params.BUILD_TYPE
                    )

                    env.SELECTED_LABEL = prediction.label
                    env.SELECTED_INSTANCE = prediction.instanceType
                    env.PREDICTED_MEMORY = prediction.predictedMemoryGb.toString()
                    env.PREDICTED_CPU = prediction.predictedCpu.toString()
                    env.PREDICTED_TIME = prediction.predictedTimeMinutes.toString()
                }
            }

            post {
                always {
                    echo "Build will start on ${env.SELECTED_LABEL} (${env.SELECTED_INSTANCE})"
                }
            }
        }
    }
}