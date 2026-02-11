import org.ml.nodeselection.GitAnalyzer
import org.ml.nodeselection.PipelineAnalyzer

/**
 * collectMetadata - Stage 1 of ML Node Selection
 * 
 * Collects ALL metadata from the workspace:
 * - Git metrics (files changed, lines added/deleted, branch, deps)
 * - Pipeline analysis (project type, dependencies, stages, tests, cache)
 * 
 * Returns the full context map that Stage 2 (mlPredict) needs.
 * 
 * Usage:
 *   def metadata = collectMetadata(buildType: 'debug')
 */
def call(Map config = [:]) {
    def buildType = config.buildType ?: 'debug'

    echo '╔══════════════════════════════════════════════════════════╗'
    echo '║          STAGE 1: METADATA COLLECTION                   ║'
    echo '╚══════════════════════════════════════════════════════════╝'

    // ========================================
    // 1. Git Analysis
    // ========================================
    echo '\n📊 Analyzing Git Changes...'
    
    def gitMetrics = [
        filesChanged: 0,
        linesAdded: 0,
        linesDeleted: 0,
        depsChanged: 0,
        branch: 'unknown'
    ]
    
    try {
        def gitAnalyzer = new GitAnalyzer(this)
        gitMetrics = gitAnalyzer.analyze()
    } catch (Exception e) {
        echo "⚠️ Git analysis failed: ${e.message}. Using defaults."
    }

    echo '┌──────────────────────────────────────┐'
    echo '│         GIT METRICS                  │'
    echo '├──────────────────────────────────────┤'
    echo "│  Files Changed   : ${gitMetrics.filesChanged}"
    echo "│  Lines Added     : ${gitMetrics.linesAdded}"
    echo "│  Lines Deleted   : ${gitMetrics.linesDeleted}"
    echo "│  Net Change      : ${gitMetrics.linesAdded - gitMetrics.linesDeleted}"
    echo "│  Deps Changed    : ${gitMetrics.depsChanged}"
    echo "│  Branch          : ${gitMetrics.branch}"
    echo '└──────────────────────────────────────┘'

    // ========================================
    // 2. Pipeline / Workspace Analysis
    // ========================================
    echo '\n🔍 Analyzing Workspace & Pipeline...'
    
    def pipelineContext = [:]
    
    try {
        def pipelineAnalyzer = new PipelineAnalyzer(this)
        pipelineContext = pipelineAnalyzer.analyze([buildType: buildType])
    } catch (Exception e) {
        echo "⚠️ Pipeline analysis failed: ${e.message}. Using git-only mode."
    }

    echo '┌──────────────────────────────────────┐'
    echo '│         WORKSPACE ANALYSIS           │'
    echo '├──────────────────────────────────────┤'
    echo "│  Project Type    : ${pipelineContext.projectType ?: 'unknown'}"
    echo "│  Repo Size       : ${pipelineContext.repoSizeMb ?: 0} MB"
    echo "│  Is Monorepo     : ${pipelineContext.isMonorepo == 1 ? 'Yes' : 'No'}"
    echo "│  Dependencies    : ${pipelineContext.dependencyCount ?: 0}"
    echo '└──────────────────────────────────────┘'

    echo '┌──────────────────────────────────────┐'
    echo '│         PIPELINE STRUCTURE           │'
    echo '├──────────────────────────────────────┤'
    echo "│  Shared Library   : ${pipelineContext.sharedLibrary ?: 'none'}"
    echo "│  Template Used    : ${pipelineContext.detectedTemplate ?: 'none'}"
    echo "│  Stages           : ${pipelineContext.stagesCount ?: 0}"
    echo "│  Build Stage      : ${pipelineContext.hasBuildStage == 1 ? '✅' : '❌'}"
    echo "│  Unit Tests       : ${pipelineContext.hasUnitTests == 1 ? '✅' : '❌'}"
    echo "│  Integration      : ${pipelineContext.hasIntegrationTests == 1 ? '✅' : '❌'}"
    echo "│  E2E Tests        : ${pipelineContext.hasE2ETests == 1 ? '✅' : '❌'}"
    echo "│  Docker Build     : ${pipelineContext.hasDockerBuild == 1 ? '✅' : '❌'}"
    echo "│  Emulator         : ${pipelineContext.usesEmulator == 1 ? '✅' : '❌'}"
    echo "│  Deploy Stage     : ${pipelineContext.hasDeployStage == 1 ? '✅' : '❌'}"
    echo "│  Parallel         : ${pipelineContext.parallelStages ?: 0}"
    echo '└──────────────────────────────────────┘'

    echo '┌──────────────────────────────────────┐'
    echo '│         BUILD CONTEXT                │'
    echo '├──────────────────────────────────────┤'
    echo "│  Build Type      : ${buildType}"
    echo "│  Branch Type     : ${pipelineContext.branch ?: 'unknown'}"
    echo "│  Environment     : ${pipelineContext.environment ?: 'development'}"
    echo "│  First Build     : ${pipelineContext.isFirstBuild == 1 ? 'Yes' : 'No'}"
    echo "│  Cache Available : ${pipelineContext.cacheAvailable == 1 ? 'Yes' : 'No'}"
    echo "│  Clean Build     : ${pipelineContext.isCleanBuild == 1 ? 'Yes' : 'No'}"
    echo '└──────────────────────────────────────┘'

    // ========================================
    // 3. Build the full ML context
    // ========================================
    def metadata = [
        // Git metrics
        filesChanged: gitMetrics.filesChanged,
        linesAdded: gitMetrics.linesAdded,
        linesDeleted: gitMetrics.linesDeleted,
        depsChanged: gitMetrics.depsChanged,
        branch: gitMetrics.branch,
        buildType: buildType,

        // Project context
        projectType: pipelineContext.projectType ?: 'python',
        repoSizeMb: pipelineContext.repoSizeMb ?: 0,
        isMonorepo: pipelineContext.isMonorepo ?: 0,
        branchType: pipelineContext.branchType ?: 0,
        environment: pipelineContext.environment ?: 'development',
        dependencyCount: pipelineContext.dependencyCount ?: 0,
        testFilesChanged: estimateTestFiles(gitMetrics),
        sourceFilesPct: estimateSourcePct(gitMetrics),

        // Pipeline structure
        stagesCount: pipelineContext.stagesCount ?: 0,
        hasBuildStage: pipelineContext.hasBuildStage ?: 0,
        hasUnitTests: pipelineContext.hasUnitTests ?: 0,
        hasIntegrationTests: pipelineContext.hasIntegrationTests ?: 0,
        hasE2ETests: pipelineContext.hasE2ETests ?: 0,
        hasDeployStage: pipelineContext.hasDeployStage ?: 0,
        hasDockerBuild: pipelineContext.hasDockerBuild ?: 0,
        usesEmulator: pipelineContext.usesEmulator ?: 0,
        parallelStages: pipelineContext.parallelStages ?: 0,
        hasArtifactPublish: pipelineContext.hasArtifactPublish ?: 0,

        // Cache state
        isFirstBuild: pipelineContext.isFirstBuild ?: 0,
        cacheAvailable: pipelineContext.cacheAvailable ?: 0,
        isCleanBuild: pipelineContext.isCleanBuild ?: 0,

        // Time context
        timeOfDayHour: pipelineContext.timeOfDayHour ?: new Date().hours,

        // Raw data (for debugging)
        gitMetrics: gitMetrics
    ]

    echo "\n✅ Metadata collection complete: ${metadata.size()} features collected"
    
    return metadata
}

/**
 * Estimate test files changed from git metrics.
 */
int estimateTestFiles(Map gitMetrics) {
    try {
        def files = gitMetrics.filesChanged ?: 0
        if (files == 0) return 0
        return Math.max(0, (files * 0.25).toInteger())
    } catch (Exception e) {
        return 0
    }
}

/**
 * Estimate source files percentage from git metrics.
 */
double estimateSourcePct(Map gitMetrics) {
    try {
        def files = gitMetrics.filesChanged ?: 0
        if (files == 0) return 0.0
        def deps = gitMetrics.depsChanged ?: 0
        def sourceFiles = Math.max(0, files - deps)
        return files > 0 ? (sourceFiles / files).toDouble() : 0.0
    } catch (Exception e) {
        return 0.0
    }
}
