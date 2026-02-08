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
    def gitAnalyzer = new GitAnalyzer(this)
    def gitMetrics = gitAnalyzer.analyze()

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
        def pipelineAnalyzer = new PipelineAnalyzer(this)
        pipelineContext = pipelineAnalyzer.analyze([buildType: buildType])
    } else {
        echo '\n⚠️ Using basic analysis (useEnhancedAnalysis=false)'
    }

    // ========================================
    // Step 3: Build Context for ML Model
    // ========================================
    def mlContext = [
        // Git metrics
        filesChanged: gitMetrics.filesChanged,
        linesAdded: gitMetrics.linesAdded,
        linesDeleted: gitMetrics.linesDeleted,
        depsChanged: gitMetrics.depsChanged,
        branch: gitMetrics.branch,
        buildType: buildType,
        
        // Enhanced context (from PipelineAnalyzer)
        projectType: pipelineContext.projectType ?: 'python',
        repoSizeMb: pipelineContext.repoSizeMb ?: 100,
        isMonorepo: pipelineContext.isMonorepo ?: 0,
        branchType: pipelineContext.branchType ?: 0,
        environment: pipelineContext.environment ?: 'development',
        dependencyCount: pipelineContext.dependencyCount ?: 50,
        testFilesChanged: estimateTestFilesChanged(gitMetrics),
        sourceFilesPct: 0.8,  // Default estimate
        
        // Pipeline structure
        stagesCount: pipelineContext.stagesCount ?: 3,
        hasBuildStage: pipelineContext.hasBuildStage ?: 1,
        hasUnitTests: pipelineContext.hasUnitTests ?: 1,
        hasIntegrationTests: pipelineContext.hasIntegrationTests ?: 0,
        hasE2ETests: pipelineContext.hasE2ETests ?: 0,
        hasDeployStage: pipelineContext.hasDeployStage ?: 0,
        hasDockerBuild: pipelineContext.hasDockerBuild ?: 0,
        usesEmulator: pipelineContext.usesEmulator ?: 0,
        parallelStages: pipelineContext.parallelStages ?: 1,
        hasArtifactPublish: pipelineContext.hasArtifactPublish ?: 0,
        
        // Cache state
        isFirstBuild: pipelineContext.isFirstBuild ?: 0,
        cacheAvailable: pipelineContext.cacheAvailable ?: 1,
        isCleanBuild: pipelineContext.isCleanBuild ?: 0,
        
        // Time context
        timeOfDayHour: pipelineContext.timeOfDayHour ?: new Date().hours
    ]

    echo '\n🤖 ML Context Summary:'
    echo "   Project: ${mlContext.projectType}"
    echo "   E2E Tests: ${mlContext.hasE2ETests ? 'Yes' : 'No'}"
    echo "   Emulator: ${mlContext.usesEmulator ? 'Yes' : 'No'}"
    echo "   Cache: ${mlContext.cacheAvailable ? 'Available' : 'None'}"
    echo "   Clean Build: ${mlContext.isCleanBuild ? 'Yes' : 'No'}"

    // ========================================
    // Step 4: Make ML Prediction
    // ========================================
    echo '\n🔮 Running ML Prediction...'
    
    def predictor = new NodePredictor(this)
    def prediction = predictor.predict(mlContext)

    echo '\n📈 ML Prediction Results:'
    echo "   Predicted CPU: ${prediction.cpu}%"
    echo "   Predicted Memory: ${prediction.memoryGb} GB"
    echo "   Predicted Time: ${prediction.timeMinutes} min"
    echo "   Confidence: ${prediction.confidence ?: 'N/A'}"
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
    env.ML_PREDICTION_CONFIDENCE = prediction.confidence ?: 'unknown'

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
        confidence: prediction.confidence,
        
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
    def files = gitMetrics.filesChanged ?: 0
    // Assume ~20-30% of changed files are test files
    return Math.max(0, (files * 0.25).toInteger())
}
