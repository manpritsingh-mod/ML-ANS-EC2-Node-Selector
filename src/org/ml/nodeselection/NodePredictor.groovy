package org.ml.nodeselection

/**
 * NodePredictor - ML prediction for AWS node selection
 * Uses a pre-trained Random Forest model
 * Trained on synthetic data for POC - replace with real data later!
 *
 * Feature Importance:
 * - lines_added: X%
 * - lines_deleted: X%
 * - net_lines: X%
 * - total_changes: X%
 * - files_changed: X%
 */
class NodePredictor implements Serializable {

    def steps

    NodePredictor(steps) {
        this.steps = steps
    }

    /**
     * Predict CPU / Memory using ML model present in workspace
     */
    Map predict(Map context, String modelPath = null) {
        try {
            // Write build context to JSON file
            steps.writeFile(
                file: 'ml_input.json',
                text: groovy.json.JsonOutput.toJson(context)
            )

            if (!steps.fileExists('ml/model.pkl')) {
                steps.echo 'ML model not found at ml/model.pkl'
            }

            if (!steps.fileExists('ml/predict.py')) {
                steps.echo 'ML predict script not found at ml/predict.py'
            }

            def output = steps.sh(
                script: '''
                    python3 -m venv .venv
                    source .venv/bin/activate
                    pip install --upgrade pip
                    pip install -r ml/requirements.txt
                    python ml/predict.py --input ml_input.json --model ml/model.pkl
                ''',
                returnStdout: true
            ).trim()

            // Parse JSON output
            return steps.readJSON(text: output)

        } catch (Exception e) {
            steps.echo "ML prediction failed: ${e.message}"
            throw e
        } finally {
            // Cleanup
            steps.sh(script: 'rm -rf .venv ml_input.json', returnStdout: true)
        }
    }
}