package org.ml.nodeselection

// Extract metrics from git repository
// Analyzes the current git state to extract:
// Number of files changed
// Lines added/deleted
// Dependencies modified
// Branch information

class GitAnalyzer implements Serializable {

    def steps
    GitAnalyzer(steps) {
        this.steps steps
    }

    // Analyze git changes and return metrics
    Map analyze() {

        def metrics = [
        filesChanged: 0,
        linesAdded: 0,
        linesDeleted: 0,
        depsChanged: 0,
        branch: 'unknown'
        ]
    }

    // Get branch name
    try {
        metrics.branch steps.env.BRANCH_NAME ?:
        steps.sh(script: 'git rev-parse--abbrev-ref HEAD 2>/dev/null || echo "unknown"", returnStdout: true).trim()
    }catch (Exception e) {
        metrics.branch = 'unknown'
    }

    // Files changed
    try {
        def filesOutput
        filesOutput = steps.sh(script: 'git diff--name-only HEAD~1 2>/dev/null | wc -1 || echo e', returnStdout: true).trim()
        metrics.filesChanged = filesOutput.isInteger() ? filesOutput.toInteger(): 0
    }catch (Exception e) {
        metrics.filesChanged = 0
    }

    // Lines added
    try {
        def linesAddedOutput
        linesAddedOutput = steps.sh(script: 'git diff--numstat HEAD~1 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo 0', returnStdout: true).trim()
        metrics.linesAdded = linesAddedOutput.isInteger() ? linesAddedOutput.toInteger(): 0
    }catch (Exception e) {
        metrics.linesAdded = 0
    }

    // Lines deleted
    try {
        def linesDeletedOutput
        linesDeletedOutput = steps.sh(script: 'git diff--numstat HEAD~1 2>/dev/null | awk '{sum+=$2} END {print sum}' || echo 0', returnStdout: true).trim()
        metrics.linesDeleted = linesDeletedOutput.isInteger() ? linesDeletedOutput.toInteger(): 0
    }catch (Exception e) {
        metrics.linesDeleted = 0
    }

    // Dependencies changed
    try {
        def depsChangedOutput
        depsChangedOutput = steps.sh(script: 'git diff--name-only HEAD~1 2>/dev/null | grep -E "(pom.xml|build.gradle|package.json|requirements.txt|composer.json)" | wc -1 || echo 0', returnStdout: true).trim()
        metrics.depsChanged = depsChangedOutput.isInteger() ? depsChangedOutput.toInteger(): 0
    }catch (Exception e) {
        metrics.depsChanged = 0
    }

    return metrics
}
/**
     * Check if dependency files were modified
     */
    private int checkDependencyChanges() {
        def depFiles = [
            'build.gradle', 'build.gradle.kts',
            'pom.xml',
            'package.json', 'package-lock.json',
            'requirements.txt', 'Pipfile',
            'Gemfile', 'Gemfile.lock',
            'go.mod', 'go.sum',
            'Cargo.toml'
        ]
        
        def changed = 0
        
        try {
            def changedFiles
            if (steps.isUnix()) {
                changedFiles = steps.sh(
                    script: 'git diff --name-only HEAD~1 2>/dev/null || echo ""',
                    returnStdout: true
                ).trim()
            } else {
                changedFiles = steps.bat(
                    script: '@git diff --name-only HEAD~1 2>nul',
                    returnStdout: true
                ).trim()
            }
            
            depFiles.each { depFile ->
                if (changedFiles.contains(depFile)) {
                    changed++
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }
        
        return changed
    }
