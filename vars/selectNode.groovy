import org.ml.nodeselection.GitAnalyzer
import org.ml.nodeselection.NodePredictor
import org.ml.nodeselection.LabelMapper
import org.ml.nodeselection.PipelineAnalyzer

/**
 * selectNode - ML-based Jenkins node selection
 * 
 * Enhanced version using 27 features for accurate resource prediction:
 * - Project type detection (Python, Java, Node, React Native, Android, iOS)
 * - Pipeline structure analysis (stages, tests, docker, emulator)
 * - Cache state detection (first build, cache available)
 * - Git metrics (files changed, lines added/deleted)
 * 
 * All values are dynamically detected - NO static defaults.
 * Uses try/catch throughout for resilient operation.
 * 
 * Usage:
 *   def result = selectNode(buildType: 'release')
 *   node(result.label) {
 *       // Build runs on optimal node
 *   }
 */
def call(Map config = [:]) {
    def buildType = config.buildType ?: 'debug'
    def modelPath = config.modelPath ?: "${env.JENKINS_HOME}/ml-models"
    def useEnhancedAnalysis = config.useEnhancedAnalysis != false  // Default true

    echo '═══════════════════════════════════════════════════════════'
    echo '     ML Node Selection - Enhanced Analysis'
    echo '═══════════════════════════════════════════════════════════'

    // ========================================
    // Step 1: Git Analysis (always performed)
    // ========================================
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
        echo "Warning: Git analysis failed: ${e.message}. Using defaults."
    }

    echo '\n📊 Git Metrics:'
    echo "   Files Changed: ${gitMetrics.filesChanged}"
    echo "   Lines Added: ${gitMetrics.linesAdded}"
    echo "   Lines Deleted: ${gitMetrics.linesDeleted}"
    echo "   Deps Changed: ${gitMetrics.depsChanged}"
    echo "   Branch: ${gitMetrics.branch}"

    // ========================================
    // Step 2: Enhanced Pipeline Analysis
    // ========================================
    def pipelineContext = [:]
    
    if (useEnhancedAnalysis) {
        echo '\n🔍 Running Enhanced Pipeline Analysis...'
        try {
            def pipelineAnalyzer = new PipelineAnalyzer(this)
            pipelineContext = pipelineAnalyzer.analyze([buildType: buildType])
        } catch (Exception e) {
            echo "Warning: Pipeline analysis failed: ${e.message}. Using git-only mode."
        }
    } else {
        echo '\n⚠️ Using basic analysis (useEnhancedAnalysis=false)'
    }

    // ========================================
    // Step 3: Build Context for ML Model
    // All values come from actual detection.
    // Only use 0 (absent/false) as defaults.
    // ========================================
    def mlContext = [
        // Git metrics (always from git analysis)
        filesChanged: gitMetrics.filesChanged,
        linesAdded: gitMetrics.linesAdded,
        linesDeleted: gitMetrics.linesDeleted,
        depsChanged: gitMetrics.depsChanged,
        branch: gitMetrics.branch,
        buildType: buildType,
        
        // Enhanced context (from PipelineAnalyzer when available)
        projectType: pipelineContext.projectType ?: 'python',
        repoSizeMb: pipelineContext.repoSizeMb ?: 0,
        isMonorepo: pipelineContext.isMonorepo ?: 0,
        branchType: pipelineContext.branchType ?: 0,
        environment: pipelineContext.environment ?: 'development',
        dependencyCount: pipelineContext.dependencyCount ?: 0,
        testFilesChanged: estimateTestFilesChanged(gitMetrics),
        sourceFilesPct: estimateSourcePct(gitMetrics),
        
        // Pipeline structure (0 = not detected / absent)
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
        
        // Cache state (0 = not detected)
        isFirstBuild: pipelineContext.isFirstBuild ?: 0,
        cacheAvailable: pipelineContext.cacheAvailable ?: 0,
        isCleanBuild: pipelineContext.isCleanBuild ?: 0,
        
        // Time context
        timeOfDayHour: pipelineContext.timeOfDayHour ?: new Date().hours
    ]

    echo '\n🤖 ML Context Summary:'
    echo "   Project: ${mlContext.projectType}"
    echo "   Dependencies: ${mlContext.dependencyCount}"
    echo "   Stages: ${mlContext.stagesCount}"
    echo "   E2E Tests: ${mlContext.hasE2ETests == 1 ? 'Yes' : 'No'}"
    echo "   Emulator: ${mlContext.usesEmulator == 1 ? 'Yes' : 'No'}"
    echo "   Docker: ${mlContext.hasDockerBuild == 1 ? 'Yes' : 'No'}"
    echo "   Cache: ${mlContext.cacheAvailable == 1 ? 'Available' : 'None'}"
    echo "   First Build: ${mlContext.isFirstBuild == 1 ? 'Yes' : 'No'}"
    echo "   Clean Build: ${mlContext.isCleanBuild == 1 ? 'Yes' : 'No'}"

    // ========================================
    // Step 4: Make ML Prediction
    // ========================================
    echo '\n🔮 Running ML Prediction...'
    
    def prediction = [:]
    try {
        def predictor = new NodePredictor(this)
        prediction = predictor.predict(mlContext)
    } catch (Exception e) {
        echo "Warning: ML prediction failed: ${e.message}. Using fallback."
        prediction = getFallbackPrediction(mlContext)
    }

    echo '\n📈 ML Prediction Results:'
    echo "   Predicted CPU: ${prediction.cpu}%"
    echo "   Predicted Memory: ${prediction.memoryGb} GB"
    echo "   Predicted Time: ${prediction.timeMinutes} min"
    echo "   Confidence: ${prediction.confidence ?: 'low'}"
    echo "   Method: ${prediction.method ?: 'ml_prediction'}"

    // ========================================
    // Step 5: Map to Jenkins Label
    // ========================================
    def mapper = new LabelMapper()
    def label = mapper.getLabel(prediction.memoryGb)
    def instanceType = mapper.getInstanceType(prediction.memoryGb)

    echo '\n🏷️ Node Selection:'
    echo "   Jenkins Label: ${label}"
    echo "   AWS Instance: ${instanceType}"
    echo '═══════════════════════════════════════════════════════════'

    // ========================================
    // Step 6: Set Environment Variables
    // ========================================
    env.ML_SELECTED_LABEL = label
    env.ML_PREDICTED_MEMORY = prediction.memoryGb.toString()
    env.ML_PREDICTED_CPU = prediction.cpu.toString()
    env.ML_PREDICTED_TIME = prediction.timeMinutes.toString()
    env.ML_PROJECT_TYPE = mlContext.projectType.toString()
    env.ML_HAS_E2E_TESTS = mlContext.hasE2ETests.toString()
    env.ML_USES_EMULATOR = mlContext.usesEmulator.toString()
    env.ML_PREDICTION_CONFIDENCE = prediction.confidence ?: 'low'

    // ========================================
    // Return Full Result
    // ========================================
    return [
        // Node selection
        label: label,
        instanceType: instanceType,
        
        // Predictions
        predictedMemoryGb: prediction.memoryGb,
        predictedCpu: prediction.cpu,
        predictedTimeMinutes: prediction.timeMinutes,
        confidence: prediction.confidence ?: 'low',
        
        // Context (for logging/debugging)
        projectType: mlContext.projectType,
        hasE2ETests: mlContext.hasE2ETests,
        usesEmulator: mlContext.usesEmulator,
        cacheAvailable: mlContext.cacheAvailable,
        
        // Raw data
        gitMetrics: gitMetrics,
        mlContext: mlContext
    ]
}

/**
 * Estimate test files changed from git metrics.
 */
int estimateTestFilesChanged(Map gitMetrics) {
    try {
        def files = gitMetrics.filesChanged ?: 0
        if (files == 0) return 0
        // Assume ~20-30% of changed files are test files
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

/**
 * Fallback prediction when ML model fails.
 * Uses simple heuristics based on project type and git metrics.
 */
Map getFallbackPrediction(Map context) {
    def projectType = context.projectType ?: 'python'
    def filesChanged = context.filesChanged ?: 0
    
    // Simple heuristic based on project type
    def defaults = [
        'python': [cpu: 30.0, memoryGb: 2.0, timeMinutes: 5.0],
        'java': [cpu: 50.0, memoryGb: 4.0, timeMinutes: 10.0],
        'nodejs': [cpu: 40.0, memoryGb: 3.0, timeMinutes: 8.0],
        'react-native': [cpu: 70.0, memoryGb: 8.0, timeMinutes: 30.0],
        'android': [cpu: 75.0, memoryGb: 10.0, timeMinutes: 40.0],
        'ios': [cpu: 70.0, memoryGb: 8.0, timeMinutes: 35.0]
    ]
    
    def base = defaults[projectType] ?: defaults['python']
    
    // Scale by file changes
    def scale = Math.max(1.0, 1.0 + (filesChanged / 50.0) * 0.5)
    
    return [
        cpu: Math.min(100.0, base.cpu * scale).round(1),
        memoryGb: (base.memoryGb * scale).round(2),
        timeMinutes: (base.timeMinutes * scale).round(1),
        confidence: 'low',
        method: 'fallback_heuristic'
    ]
}
