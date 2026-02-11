package org.ml.nodeselection

/**
 * GitAnalyzer - Extract metrics from git repository
 * Analyzes the current git state to extract:
 * - Number of files changed
 * - Lines added/deleted
 * - Dependencies modified
 * - Branch information
 * 
 * PLATFORM: Windows (bat commands)
 * For Linux: Replace 'bat' with 'sh' and adjust commands accordingly
 */
class GitAnalyzer implements Serializable {

    def steps

    GitAnalyzer(steps) {
        this.steps = steps
    }

    /**
     * Analyze git changes and return metrics
     */
    Map analyze() {
        def metrics = [
            filesChanged: 0,
            linesAdded: 0,
            linesDeleted: 0,
            depsChanged: 0,
            branch: 'unknown'
        ]

        // Get branch name
        try {
            metrics.branch = steps.env.BRANCH_NAME ?: getBranchName()
        } catch (Exception e) {
            metrics.branch = 'unknown'
        }

        // Files changed
        try {
            metrics.filesChanged = getFilesChanged()
        } catch (Exception e) {
            metrics.filesChanged = 0
        }

        // Lines added
        try {
            metrics.linesAdded = getLinesAdded()
        } catch (Exception e) {
            metrics.linesAdded = 0
        }

        // Lines deleted
        try {
            metrics.linesDeleted = getLinesDeleted()
        } catch (Exception e) {
            metrics.linesDeleted = 0
        }

        // Dependencies changed
        try {
            metrics.depsChanged = checkDependencyChanges()
        } catch (Exception e) {
            metrics.depsChanged = 0
        }

        return metrics
    }

    /**
     * Get the current branch name
     */
    private String getBranchName() {
        // ============ WINDOWS ============
        def output = steps.bat(
            script: '@git rev-parse --abbrev-ref HEAD 2>nul || echo unknown',
            returnStdout: true
        ).trim()
        def lines = output.split('\n')
        return lines.length > 0 ? lines[-1].trim() : 'unknown'

        // ============ UBUNTU/LINUX (commented) ============
        // def output = steps.sh(
        //     script: 'git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown',
        //     returnStdout: true
        // ).trim()
        // return output ?: 'unknown'
    }

    /**
     * Get number of files changed
     */
    private int getFilesChanged() {
        try {
            // ============ WINDOWS ============
            def output = steps.bat(
                script: '@git diff --name-only HEAD~1 2>nul',
                returnStdout: true
            ).trim()

            // ============ UBUNTU/LINUX (commented) ============
            // def output = steps.sh(
            //     script: 'git diff --name-only HEAD~1 2>/dev/null || true',
            //     returnStdout: true
            // ).trim()
            
            if (output.isEmpty()) return 0
            def lines = output.split('\n').findAll { it.trim() }
            return lines.size()
        } catch (Exception e) {
            return 0
        }
    }

    /**
     * Get number of lines added
     */
    private int getLinesAdded() {
        try {
            // ============ WINDOWS ============
            def output = steps.bat(
                script: '@git diff --numstat HEAD~1 2>nul',
                returnStdout: true
            ).trim()

            // ============ UBUNTU/LINUX (commented) ============
            // def output = steps.sh(
            //     script: 'git diff --numstat HEAD~1 2>/dev/null || true',
            //     returnStdout: true
            // ).trim()
            
            if (output.isEmpty()) return 0
            
            int total = 0
            output.split('\n').each { line ->
                def parts = line.trim().split(/\s+/)
                if (parts.length >= 1 && parts[0].isInteger()) {
                    total += parts[0].toInteger()
                }
            }
            return total
        } catch (Exception e) {
            return 0
        }
    }

    /**
     * Get number of lines deleted
     */
    private int getLinesDeleted() {
        try {
            // ============ WINDOWS ============
            def output = steps.bat(
                script: '@git diff --numstat HEAD~1 2>nul',
                returnStdout: true
            ).trim()

            // ============ UBUNTU/LINUX (commented) ============
            // def output = steps.sh(
            //     script: 'git diff --numstat HEAD~1 2>/dev/null || true',
            //     returnStdout: true
            // ).trim()
            
            if (output.isEmpty()) return 0
            
            int total = 0
            output.split('\n').each { line ->
                def parts = line.trim().split(/\s+/)
                if (parts.length >= 2 && parts[1].isInteger()) {
                    total += parts[1].toInteger()
                }
            }
            return total
        } catch (Exception e) {
            return 0
        }
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
            // ============ WINDOWS ============
            def changedFiles = steps.bat(
                script: '@git diff --name-only HEAD~1 2>nul',
                returnStdout: true
            ).trim()

            // ============ UBUNTU/LINUX (commented) ============
            // def changedFiles = steps.sh(
            //     script: 'git diff --name-only HEAD~1 2>/dev/null || true',
            //     returnStdout: true
            // ).trim()

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
}
