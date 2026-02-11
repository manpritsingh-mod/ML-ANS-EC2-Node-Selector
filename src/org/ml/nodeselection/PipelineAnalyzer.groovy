package org.ml.nodeselection

/**
 * PipelineAnalyzer - Detect build context for ML prediction
 * 
 * Analyzes the workspace and Jenkinsfile to detect:
 * 1. Project type (Python, Java, Node, React Native, Android, iOS)
 * 2. Pipeline structure (stages, tests, docker, emulator)
 * 3. Cache state (first build, cache available)
 * 4. Repository metrics (size, dependencies)
 * 
 * This context enables accurate ML resource prediction by providing
 * all 27 features required by the enhanced model.
 * 
 * PLATFORM: Windows (bat commands)
 * For Linux: Replace 'bat' with 'sh' and adjust commands accordingly
 */
class PipelineAnalyzer implements Serializable {

    def steps
    
    // Project type constants (must match train_model.py PROJECT_TYPES)
    static final Map PROJECT_TYPES = [
        'python': 0,
        'java': 1,
        'nodejs': 2,
        'react-native': 3,
        'android': 4,
        'ios': 5
    ]
    
    // Branch type constants
    static final Map BRANCH_TYPES = [
        'feature': 0,
        'develop': 1,
        'main': 2,
        'hotfix': 3,
        'release': 4
    ]

    PipelineAnalyzer(steps) {
        this.steps = steps
    }

    /**
     * Analyze the workspace and return full build context for ML prediction.
     * 
     * @param config Optional configuration overrides
     * @return Map containing all 27 features for ML model
     */
    Map analyze(Map config = [:]) {
        steps.echo "PipelineAnalyzer: Starting workspace analysis..."
        
        def context = [:]
        
        // 1. Detect project type
        context.projectType = detectProjectType()
        steps.echo "  Project Type: ${context.projectType}"
        
        // 2. Get repository size
        context.repoSizeMb = getRepoSize()
        steps.echo "  Repo Size: ${context.repoSizeMb} MB"
        
        // 3. Detect if monorepo
        context.isMonorepo = detectMonorepo()
        steps.echo "  Is Monorepo: ${context.isMonorepo}"
        
        // 4. Analyze branch
        def branchInfo = analyzeBranch()
        context.branch = branchInfo.name
        context.branchType = branchInfo.type
        steps.echo "  Branch: ${context.branch} (type: ${branchInfo.typeName})"
        
        // 5. Build type from config or env
        context.buildType = config.buildType ?: (steps.env.BUILD_TYPE ?: 'debug')
        steps.echo "  Build Type: ${context.buildType}"
        
        // 6. Environment
        context.environment = detectEnvironment()
        steps.echo "  Environment: ${context.environment}"
        
        // 7. Analyze pipeline structure
        def pipelineInfo = analyzePipelineStructure()
        context.putAll(pipelineInfo)
        steps.echo "  Stages: ${pipelineInfo.stagesCount}, E2E: ${pipelineInfo.hasE2ETests}, Emulator: ${pipelineInfo.usesEmulator}"
        
        // 8. Count dependencies
        context.dependencyCount = countDependencies(context.projectType)
        steps.echo "  Dependencies: ${context.dependencyCount}"
        
        // 9. Check cache state
        def cacheInfo = checkCacheState(context.projectType)
        context.isFirstBuild = cacheInfo.isFirstBuild
        context.cacheAvailable = cacheInfo.cacheAvailable
        steps.echo "  Cache Available: ${cacheInfo.cacheAvailable}"
        
        // 10. Check if clean build requested
        context.isCleanBuild = isCleanBuildRequested()
        steps.echo "  Clean Build: ${context.isCleanBuild}"
        
        // 11. Time of day
        context.timeOfDayHour = new Date().hours
        
        // 12. Artifact publish (tied to release builds)
        context.hasArtifactPublish = (context.buildType.toLowerCase() == 'release') ? 1 : 0
        
        steps.echo "PipelineAnalyzer: Analysis complete!"
        
        return context
    }

    /**
     * Detect project type from workspace files.
     */
    String detectProjectType() {
        // Check for project indicators in priority order
        if (steps.fileExists('package.json')) {
            // Could be Node.js or React Native
            if (steps.fileExists('android') || steps.fileExists('ios')) {
                return 'react-native'
            }
            // Check package.json for react-native dependency
            try {
                def pkg = steps.readJSON(file: 'package.json')
                def deps = (pkg.dependencies ?: [:]) + (pkg.devDependencies ?: [:])
                if (deps.containsKey('react-native')) {
                    return 'react-native'
                }
            } catch (e) {
                // Ignore parse errors
            }
            return 'nodejs'
        }
        
        if (steps.fileExists('build.gradle') || steps.fileExists('build.gradle.kts')) {
            // Could be Android or Java
            if (steps.fileExists('android') || steps.fileExists('AndroidManifest.xml')) {
                return 'android'
            }
            def gradleContent = ''
            try {
                gradleContent = steps.readFile('build.gradle')
            } catch (e) {
                gradleContent = ''
            }
            if (gradleContent.contains('com.android')) {
                return 'android'
            }
            return 'java'
        }
        
        if (steps.fileExists('pom.xml')) {
            return 'java'
        }
        
        if (steps.fileExists('Podfile') || steps.fileExists('*.xcodeproj') || steps.fileExists('*.xcworkspace')) {
            return 'ios'
        }
        
        if (steps.fileExists('requirements.txt') || steps.fileExists('setup.py') || steps.fileExists('pyproject.toml')) {
            return 'python'
        }
        
        // Default to Python (most lightweight)
        return 'python'
    }

    /**
     * Get repository size in MB.
     */
    int getRepoSize() {
        try {
            // ============ WINDOWS ============
            def output = steps.bat(
                script: '@powershell -Command "(Get-ChildItem -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB" 2>nul',
                returnStdout: true
            ).trim()
            def lines = output.split('\n')
            def lastLine = lines[-1].trim()
            return lastLine.isNumber() ? Math.round(lastLine.toDouble()).toInteger() : 100

            // ============ UBUNTU/LINUX (commented) ============
            // def output = steps.sh(
            //     script: 'du -sm . 2>/dev/null | cut -f1',
            //     returnStdout: true
            // ).trim()
            // return output.isInteger() ? output.toInteger() : 100
        } catch (e) {
            return 100  // Default estimate
        }
    }

    /**
     * Detect if this is a monorepo (multiple package.json files or workspaces).
     */
    int detectMonorepo() {
        try {
            // Check for common monorepo indicators
            if (steps.fileExists('lerna.json') || steps.fileExists('pnpm-workspace.yaml')) {
                return 1
            }
            
            // Check package.json for workspaces
            if (steps.fileExists('package.json')) {
                def pkg = steps.readJSON(file: 'package.json')
                if (pkg.workspaces) {
                    return 1
                }
            }
            
            // ============ WINDOWS ============
            def output = steps.bat(
                script: '@powershell -Command "(Get-ChildItem -Recurse -Name package.json).Count" 2>nul',
                returnStdout: true
            ).trim()
            def lines = output.split('\n')
            def lastLine = lines[-1].trim()
            if (lastLine.isInteger() && lastLine.toInteger() > 2) {
                return 1
            }

            // ============ UBUNTU/LINUX (commented) ============
            // def output = steps.sh(
            //     script: 'find . -name "package.json" -type f 2>/dev/null | wc -l',
            //     returnStdout: true
            // ).trim()
            // if (output.isInteger() && output.toInteger() > 2) {
            //     return 1
            // }
            
            return 0
        } catch (e) {
            return 0
        }
    }

    /**
     * Analyze current branch.
     */
    Map analyzeBranch() {
        def branchName = steps.env.BRANCH_NAME ?: steps.env.GIT_BRANCH ?: 'develop'
        branchName = branchName.replaceAll('origin/', '')
        
        def branchType = 0  // default: feature
        def typeName = 'feature'
        
        if (branchName.toLowerCase().contains('feature')) {
            branchType = 0
            typeName = 'feature'
        } else if (branchName.toLowerCase() in ['develop', 'development']) {
            branchType = 1
            typeName = 'develop'
        } else if (branchName.toLowerCase() in ['main', 'master']) {
            branchType = 2
            typeName = 'main'
        } else if (branchName.toLowerCase().contains('hotfix')) {
            branchType = 3
            typeName = 'hotfix'
        } else if (branchName.toLowerCase().contains('release')) {
            branchType = 4
            typeName = 'release'
        }
        
        return [name: branchName, type: branchType, typeName: typeName]
    }

    /**
     * Detect deployment environment.
     */
    String detectEnvironment() {
        def env = steps.env.DEPLOY_ENV ?: steps.env.ENVIRONMENT ?: 'development'
        
        if (env.toLowerCase() in ['prod', 'production']) {
            return 'production'
        } else if (env.toLowerCase() == 'staging') {
            return 'staging'
        }
        return 'development'
    }

    /**
     * Analyze pipeline structure from Jenkinsfile or common patterns.
     */
    Map analyzePipelineStructure() {
        def info = [
            stagesCount: 3,           // Default: checkout, build, test
            hasBuildStage: 1,
            hasUnitTests: 1,
            hasIntegrationTests: 0,
            hasE2ETests: 0,
            hasDeployStage: 0,
            hasDockerBuild: 0,
            usesEmulator: 0,
            parallelStages: 1
        ]
        
        try {
            // Check for Jenkinsfile
            if (steps.fileExists('Jenkinsfile')) {
                def jenkinsfile = steps.readFile('Jenkinsfile')
                
                // Count stage blocks
                def stageMatches = (jenkinsfile =~ /stage\s*\(/)
                info.stagesCount = stageMatches.size() ?: 3
                
                // Detect test types
                if (jenkinsfile.toLowerCase().contains('integration') || 
                    jenkinsfile.toLowerCase().contains('integrationtest')) {
                    info.hasIntegrationTests = 1
                }
                
                if (jenkinsfile.toLowerCase() =~ /(e2e|appium|selenium|detox|cypress)/) {
                    info.hasE2ETests = 1
                }
                
                // Detect Docker
                if (jenkinsfile.toLowerCase() =~ /(docker\s+build|dockerfile|docker\.build)/) {
                    info.hasDockerBuild = 1
                }
                
                // Detect emulator
                if (jenkinsfile.toLowerCase() =~ /(emulator|simulator|avd|xctest)/) {
                    info.usesEmulator = 1
                }
                
                // Detect deploy
                if (jenkinsfile.toLowerCase() =~ /(deploy|publish|release|upload)/) {
                    info.hasDeployStage = 1
                }
                
                // Detect parallel
                def parallelMatches = (jenkinsfile =~ /parallel\s*\{/)
                if (parallelMatches.size() > 0) {
                    info.parallelStages = 2
                }
            }
            
            // Additional detection from workspace
            if (steps.fileExists('e2e') || steps.fileExists('__tests__/e2e') || 
                steps.fileExists('tests/e2e') || steps.fileExists('cypress')) {
                info.hasE2ETests = 1
            }
            
            if (steps.fileExists('Dockerfile') || steps.fileExists('docker-compose.yml')) {
                info.hasDockerBuild = 1
            }
            
            // For mobile projects, check for test dependencies
            if (steps.fileExists('package.json')) {
                def pkg = steps.readJSON(file: 'package.json')
                def deps = (pkg.dependencies ?: [:]) + (pkg.devDependencies ?: [:])
                
                if (deps.containsKey('detox') || deps.containsKey('appium')) {
                    info.hasE2ETests = 1
                    info.usesEmulator = 1
                }
            }
            
        } catch (e) {
            steps.echo "Warning: Could not analyze pipeline structure: ${e.message}"
        }
        
        return info
    }

    /**
     * Count project dependencies.
     */
    int countDependencies(String projectType) {
        try {
            switch (projectType) {
                case 'nodejs':
                case 'react-native':
                    if (steps.fileExists('package.json')) {
                        def pkg = steps.readJSON(file: 'package.json')
                        def deps = (pkg.dependencies ?: [:]).size()
                        def devDeps = (pkg.devDependencies ?: [:]).size()
                        return deps + devDeps
                    }
                    break
                    
                case 'python':
                    if (steps.fileExists('requirements.txt')) {
                        def content = steps.readFile('requirements.txt')
                        return content.split('\n').findAll { it.trim() && !it.startsWith('#') }.size()
                    }
                    break
                    
                case 'java':
                case 'android':
                    // Estimate from build.gradle
                    if (steps.fileExists('build.gradle')) {
                        def content = steps.readFile('build.gradle')
                        def deps = (content =~ /implementation|compile|api/).size()
                        return deps ?: 50
                    }
                    break
            }
        } catch (e) {
            // Ignore errors
        }
        
        // Default estimates by project type
        def defaults = [
            'python': 30,
            'java': 50,
            'nodejs': 100,
            'react-native': 150,
            'android': 80,
            'ios': 40
        ]
        
        return defaults[projectType] ?: 50
    }

    /**
     * Check cache state for dependencies.
     */
    Map checkCacheState(String projectType) {
        def isFirstBuild = 0
        def cacheAvailable = 0
        
        try {
            // Check if this is first build for this job
            def buildNumber = steps.env.BUILD_NUMBER?.toInteger() ?: 1
            if (buildNumber == 1) {
                isFirstBuild = 1
            }
            
            // Check for cache directories
            switch (projectType) {
                case 'nodejs':
                case 'react-native':
                    if (steps.fileExists('node_modules')) {
                        cacheAvailable = 1
                    }
                    break
                    
                case 'python':
                    if (steps.fileExists('.venv') || steps.fileExists('__pycache__')) {
                        cacheAvailable = 1
                    }
                    break
                    
                case 'java':
                case 'android':
                    // Check for local Maven/Gradle cache
                    if (steps.fileExists('.gradle') || steps.fileExists('build')) {
                        cacheAvailable = 1
                    }
                    break
                    
                case 'ios':
                    if (steps.fileExists('Pods')) {
                        cacheAvailable = 1
                    }
                    break
            }
            
            // Check for CI cache plugin markers
            if (steps.fileExists('.cache') || steps.env.CACHE_HIT == 'true') {
                cacheAvailable = 1
            }
            
        } catch (e) {
            // Default to no cache on error
        }
        
        return [isFirstBuild: isFirstBuild, cacheAvailable: cacheAvailable]
    }

    /**
     * Check if clean build is requested via env or params.
     */
    int isCleanBuildRequested() {
        // Check common clean build indicators
        if (steps.env.CLEAN_BUILD?.toLowerCase() in ['true', '1', 'yes']) {
            return 1
        }
        
        if (steps.env.FORCE_CLEAN?.toLowerCase() in ['true', '1', 'yes']) {
            return 1
        }
        
        // Check if --clean flag in build command
        def buildArgs = steps.env.BUILD_ARGS ?: ''
        if (buildArgs.contains('--clean') || buildArgs.contains('clean')) {
            return 1
        }
        
        return 0
    }
}
