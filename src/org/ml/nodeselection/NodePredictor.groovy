package org.ml.nodeselection

/**
 * NodePredictor - ML prediction for AWS node selection
 * Uses a pre-trained Random Forest model
 * Trained on synthetic data for POC - replace with real data later!
 *
 * PLATFORM: Ubuntu/Linux (sh commands)
 * For Windows: Replace 'sh' with 'bat' and adjust paths accordingly
 *
 * Feature Importance:
 * - lines_added: X%
 * - lines_deleted: X%
 * - net_lines: X%
 * - total_changes: X%
 * - files_changed: X%
 * 
 * KNOWN ISSUES FIXED:
 * - Multiline scripts collapse in Jenkins: Use separate sh/bat calls
 * - pip upgrade permission error: Use python -m pip
 * - activate.bat hanging: Use direct venv python path
 * - pip prompts hanging: Use --disable-pip-version-check --no-input -q flags
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

            // ============ UBUNTU/LINUX ============
            // Step 1: Clean up existing venv
            steps.sh script: 'rm -rf .venv || true', returnStatus: true

            // Step 2: Create virtual environment
            steps.sh script: 'python3 -m venv .venv', returnStatus: true

            // Step 3: Install dependencies (using full path to avoid activate issues)
            steps.sh script: '.venv/bin/python -m pip install --disable-pip-version-check --no-input -q -r ml/requirements.txt', returnStatus: true

            // Step 4: Run prediction script
            def output = steps.sh(
                script: '.venv/bin/python ml/predict.py --input ml_input.json --model ml/model.pkl',
                returnStdout: true
            ).trim()
            
            // ============ WINDOWS (commented) ============
            // // Step 1: Clean up existing venv
            // steps.bat script: '@if exist .venv rmdir /s /q .venv', returnStatus: true
            //
            // // Step 2: Create virtual environment
            // steps.bat script: '@python -m venv .venv', returnStatus: true
            //
            // // Step 3: Install dependencies (using full path to avoid activate issues)
            // steps.bat script: '@.venv\\Scripts\\python.exe -m pip install --disable-pip-version-check --no-input -q -r ml\\requirements.txt', returnStatus: true
            //
            // // Step 4: Run prediction script
            // def output = steps.bat(
            //     script: '@.venv\\Scripts\\python.exe ml\\predict.py --input ml_input.json --model ml\\model.pkl',
            //     returnStdout: true
            // ).trim()

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
            // ============ UBUNTU/LINUX CLEANUP ============
            steps.sh script: 'rm -rf .venv || true', returnStatus: true
            steps.sh script: 'rm -f ml_input.json || true', returnStatus: true
            
            // ============ WINDOWS CLEANUP (commented) ============
            // steps.bat script: '@if exist .venv rmdir /s /q .venv', returnStatus: true
            // steps.bat script: '@if exist ml_input.json del /f ml_input.json', returnStatus: true
        }
    }
}