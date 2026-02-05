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

            // Step 1: Clean up existing venv
            steps.bat script: '@if exist .venv rmdir /s /q .venv', returnStatus: true

            // Step 2: Create virtual environment
            steps.bat script: '@python -m venv .venv', returnStatus: true

            // Step 3: Install dependencies (using full path to avoid activate issues)
            steps.bat script: '@.venv\\Scripts\\python.exe -m pip install --disable-pip-version-check --no-input -q -r ml\\requirements.txt', returnStatus: true

            // Step 4: Run prediction script
            def output = steps.bat(
                script: '@.venv\\Scripts\\python.exe ml\\predict.py --input ml_input.json --model ml\\model.pkl',
                returnStdout: true
            ).trim()

            // Extract JSON from output (last line should be the JSON)
            def lines = output.split('\r?\n')
            def jsonLine = lines.findAll { line ->
                line.trim().startsWith('{') && line.trim().endsWith('}')
            }

            if (jsonLine.isEmpty()) {
                steps.echo "No JSON found in output: ${output}"
                throw new Exception("ML prediction did not return valid JSON")
            }

            // Parse JSON output
            return steps.readJSON(text: jsonLine.last().trim())

        } catch (Exception e) {
            steps.echo "ML prediction failed: ${e.message}"
            throw e
        } finally {
            // Cleanup
            steps.bat script: '@if exist .venv rmdir /s /q .venv', returnStatus: true
            steps.bat script: '@if exist ml_input.json del /f ml_input.json', returnStatus: true
        }
    }
}