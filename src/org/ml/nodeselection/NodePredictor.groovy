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

            // Windows-compatible Python execution
            def output = steps.bat(
                script: '''
                    @echo off
                    setlocal
                    
                    REM Remove existing venv to avoid conflicts
                    if exist .venv rmdir /s /q .venv
                    
                    REM Create fresh virtual environment
                    python -m venv .venv
                    if errorlevel 1 exit /b 1
                    
                    REM Activate venv
                    call .venv\\Scripts\\activate.bat
                    
                    REM Install dependencies with no prompts
                    python -m pip install --disable-pip-version-check --no-input -q -r ml\\requirements.txt
                    if errorlevel 1 exit /b 1
                    
                    REM Run prediction
                    python ml\\predict.py --input ml_input.json --model ml\\model.pkl
                ''',
                returnStdout: true
            ).trim()

            // Extract JSON from bat output (skip command echoes)
            def jsonLine = output.split('\n').findAll { line ->
                line.trim().startsWith('{') && line.trim().endsWith('}')
            }.last()

            // Parse JSON output
            return steps.readJSON(text: jsonLine)

        } catch (Exception e) {
            steps.echo "ML prediction failed: ${e.message}"
            throw e
        } finally {
            // Cleanup - Windows compatible
            steps.bat(script: '@if exist .venv rmdir /s /q .venv', returnStdout: true)
            steps.bat(script: '@if exist ml_input.json del /f ml_input.json', returnStdout: true)
        }
    }
}