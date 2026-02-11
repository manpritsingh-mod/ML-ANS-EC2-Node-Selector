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
            return lastLine.isNumber() ? Math.round(lastLine.toDouble()).toInteger() : 0

            // ============ UBUNTU/LINUX (commented) ============
            // def output = steps.sh(
            //     script: 'du -sm . 2>/dev/null | cut -f1',
            //     returnStdout: true
            // ).trim()
            // return output.isInteger() ? output.toInteger() : 0
        } catch (e) {
            return 0  // Could not determine repo size
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
     * Analyze pipeline structure from Jenkinsfile.
     * ALWAYS scans the actual Jenkinsfile content directly.
     * Detects stages, build tools, tests, docker, emulator, deploy keywords.
     * No hardcoded values — everything comes from the project's Jenkinsfile.
     */
    Map analyzePipelineStructure() {
        def info = [
            stagesCount: 0,
            hasBuildStage: 0,
            hasUnitTests: 0,
            hasIntegrationTests: 0,
            hasE2ETests: 0,
            hasDeployStage: 0,
            hasDockerBuild: 0,
            usesEmulator: 0,
            parallelStages: 0,
            detectedTemplate: 'none',
            sharedLibrary: 'none'
        ]
        
        try {
            if (!steps.fileExists('Jenkinsfile')) {
                steps.echo "  No Jenkinsfile found in workspace"
                return info
            }

            def jenkinsfile = steps.readFile('Jenkinsfile')
            def jfLower = jenkinsfile.toLowerCase()
            
            // ──────────────────────────────────────────────
            // 1. Detect shared library (display only)
            // ──────────────────────────────────────────────
            try {
                def libraryMatch = (jenkinsfile =~ /@Library\s*\(\s*['"(\[]([^)\]'\"]+)/)
                if (libraryMatch.size() > 0) {
                    info.sharedLibrary = libraryMatch[0][1].trim()
                    steps.echo "  Shared Library: ${info.sharedLibrary}"
                }
            } catch (e) {
                // Ignore regex errors
            }
            
            // ──────────────────────────────────────────────
            // 2. Detect template name (display only, no hardcoded stages)
            // ──────────────────────────────────────────────
            def templateNames = [
                'javaMaven_template', 'java_maven_template',
                'python_template', 'pythonPipeline_template',
                'nodejs_template', 'node_template',
                'reactNative_template', 'react_native_template',
                'android_template', 'androidGradle_template',
                'ios_template', 'iosXcode_template'
            ]
            
            for (tmpl in templateNames) {
                if (jfLower.contains(tmpl.toLowerCase())) {
                    info.detectedTemplate = tmpl
                    steps.echo "  Template: ${tmpl}"
                    break
                }
            }
            
            // ──────────────────────────────────────────────
            // 3. Count actual stage blocks
            // ──────────────────────────────────────────────
            try {
                def stageMatches = (jenkinsfile =~ /stage\s*\(/)
                info.stagesCount = stageMatches.size()
            } catch (e) {
                info.stagesCount = 0
            }
            
            // ──────────────────────────────────────────────
            // 4. Detect BUILD stage (build tools / commands)
            // ──────────────────────────────────────────────
            if (jfLower =~ /(mvn |mvn\s+|maven|gradle |gradle\s+|npm run build|npm install|pip install|go build|make |cmake|ant |msbuild|dotnet build|cargo build)/) {
                info.hasBuildStage = 1
            }
            // Also check tools block
            if (jfLower =~ /tools\s*\{[^}]*(maven|gradle|jdk|nodejs|go|python)/) {
                info.hasBuildStage = 1
            }
            
            // ──────────────────────────────────────────────
            // 5. Detect UNIT TESTS
            // ──────────────────────────────────────────────
            if (jfLower =~ /(unit\s*test|mvn test|mvn\s+test|gradle test|pytest|npm test|npm run test|go test|jest|mocha|junit|nunit|xunit|rspec|phpunit|cargo test)/) {
                info.hasUnitTests = 1
            }
            // Check config flags
            if (jenkinsfile =~ /(?i)runUnitTests\s*:\s*true/) {
                info.hasUnitTests = 1
            }
            if (jenkinsfile =~ /(?i)runUnitTests\s*:\s*false/) {
                info.hasUnitTests = 0
            }
            
            // ──────────────────────────────────────────────
            // 6. Detect INTEGRATION TESTS
            // ──────────────────────────────────────────────
            if (jfLower =~ /(integration\s*test|mvn verify|mvn\s+verify|failsafe|integration-test|integrationtest)/) {
                info.hasIntegrationTests = 1
            }
            if (jenkinsfile =~ /(?i)runIntegrationTests\s*:\s*true/) {
                info.hasIntegrationTests = 1
            }
            if (jenkinsfile =~ /(?i)runIntegrationTests\s*:\s*false/) {
                info.hasIntegrationTests = 0
            }
            
            // ──────────────────────────────────────────────
            // 7. Detect E2E TESTS
            // ──────────────────────────────────────────────
            if (jfLower =~ /(e2e|end.to.end|appium|selenium|detox|cypress|playwright|puppeteer|nightwatch|testcafe)/) {
                info.hasE2ETests = 1
            }
            if (jenkinsfile =~ /(?i)runE2E\s*:\s*true/) {
                info.hasE2ETests = 1
            }
            
            // ──────────────────────────────────────────────
            // 8. Detect DOCKER
            // ──────────────────────────────────────────────
            if (jfLower =~ /(docker\s+build|docker\.build|docker-compose|dockerfile|docker push|docker tag|podman)/) {
                info.hasDockerBuild = 1
            }
            if (jenkinsfile =~ /(?i)dockerBuild\s*:\s*true/) {
                info.hasDockerBuild = 1
            }
            
            // ──────────────────────────────────────────────
            // 9. Detect EMULATOR / SIMULATOR
            // ──────────────────────────────────────────────
            if (jfLower =~ /(emulator|simulator|avd|xctest|xcrun|instruments|android\s*emulator|ios\s*simulator)/) {
                info.usesEmulator = 1
            }
            
            // ──────────────────────────────────────────────
            // 10. Detect DEPLOY stage
            // ──────────────────────────────────────────────
            if (jfLower =~ /(deploy|publish|release|upload|aws\s|kubectl|helm|ansible|terraform|s3\s|ecr|ecs|gcloud|az\s|heroku|netlify|vercel)/) {
                info.hasDeployStage = 1
            }
            if (jenkinsfile =~ /(?i)deployEnabled\s*:\s*true/) {
                info.hasDeployStage = 1
            }
            
            // ──────────────────────────────────────────────
            // 11. Detect PARALLEL stages
            // ──────────────────────────────────────────────
            try {
                def parallelMatches = (jenkinsfile =~ /parallel\s*\{/)
                if (parallelMatches.size() > 0) {
                    info.parallelStages = parallelMatches.size()
                }
            } catch (e) {
                // Ignore
            }
            
            // ──────────────────────────────────────────────
            // 12. Detect from WORKSPACE files (not Jenkinsfile)
            // ──────────────────────────────────────────────
            if (steps.fileExists('e2e') || steps.fileExists('__tests__/e2e') || 
                steps.fileExists('tests/e2e') || steps.fileExists('cypress') ||
                steps.fileExists('cypress.config.js')) {
                info.hasE2ETests = 1
            }
            
            if (steps.fileExists('Dockerfile') || steps.fileExists('docker-compose.yml') ||
                steps.fileExists('docker-compose.yaml')) {
                info.hasDockerBuild = 1
            }
            
            // Mobile project test dependencies
            if (steps.fileExists('package.json')) {
                try {
                    def pkg = steps.readJSON(file: 'package.json')
                    def deps = (pkg.dependencies ?: [:]) + (pkg.devDependencies ?: [:])
                    
                    if (deps.containsKey('detox') || deps.containsKey('appium')) {
                        info.hasE2ETests = 1
                        info.usesEmulator = 1
                    }
                    if (deps.containsKey('jest') || deps.containsKey('mocha') || deps.containsKey('vitest')) {
                        info.hasUnitTests = 1
                    }
                    if (deps.containsKey('cypress') || deps.containsKey('playwright')) {
                        info.hasE2ETests = 1
                    }
                } catch (e) {
                    // Ignore parse errors
                }
            }
            
            // Maven surefire / failsafe from pom.xml
            if (steps.fileExists('pom.xml')) {
                try {
                    def pom = steps.readFile('pom.xml')
                    if (pom.contains('maven-surefire-plugin') || pom.contains('src/test')) {
                        info.hasUnitTests = 1
                    }
                    if (pom.contains('maven-failsafe-plugin')) {
                        info.hasIntegrationTests = 1
                    }
                } catch (e) {
                    // Ignore
                }
            }
            
        } catch (e) {
            steps.echo "Warning: Could not analyze pipeline structure: ${e.message}"
        }
        
        return info
    }

    /**
     * Count project dependencies dynamically.
     * Never returns static defaults - returns 0 if cannot detect.
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
                    if (steps.fileExists('setup.py')) {
                        def content = steps.readFile('setup.py')
                        def deps = (content =~ /install_requires/).size()
                        return deps > 0 ? (content =~ /['"][a-zA-Z]/).size() : 0
                    }
                    break
                    
                case 'java':
                case 'android':
                    // Try pom.xml first (Maven)
                    if (steps.fileExists('pom.xml')) {
                        try {
                            def content = steps.readFile('pom.xml')
                            def deps = (content =~ /<dependency>/).size()
                            return deps
                        } catch (e) {
                            // Fall through
                        }
                    }
                    // Try build.gradle (Gradle)
                    if (steps.fileExists('build.gradle')) {
                        try {
                            def content = steps.readFile('build.gradle')
                            def deps = (content =~ /implementation |compile |api |testImplementation /).size()
                            return deps
                        } catch (e) {
                            // Fall through
                        }
                    }
                    break
                    
                case 'ios':
                    if (steps.fileExists('Podfile')) {
                        def content = steps.readFile('Podfile')
                        return (content =~ /pod '/).size()
                    }
                    break
            }
        } catch (e) {
            steps.echo "Warning: Could not count dependencies: ${e.message}"
        }
        
        // Return 0 when we cannot dynamically detect
        return 0
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
