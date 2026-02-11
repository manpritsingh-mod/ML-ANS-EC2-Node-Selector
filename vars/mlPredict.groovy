import org.ml.nodeselection.NodePredictor
import org.ml.nodeselection.LabelMapper

/**
 * mlPredict - Stage 2 of ML Node Selection
 * 
 * Takes the metadata collected in Stage 1 and:
 * 1. Feeds it to the Random Forest ML model
 * 2. Gets CPU, Memory, Build Time predictions
 * 3. Maps prediction to the best AWS EC2 node
 * 
 * Usage:
 *   def metadata = collectMetadata(buildType: 'debug')
 *   def result = mlPredict(metadata: metadata)
 */
def call(Map config = [:]) {
    def metadata = config.metadata ?: [:]

    echo '╔══════════════════════════════════════════════════════════╗'
    echo '║          STAGE 2: ML PREDICTION & NODE SELECTION        ║'
    echo '╚══════════════════════════════════════════════════════════╝'

    // ========================================
    // 1. Run ML Prediction
    // ========================================
    echo '\n🔮 Feeding metadata to Random Forest Model...'
    echo "   Features: 27"
    echo "   Model: ml/model.pkl"

    def prediction = [:]
    try {
        def predictor = new NodePredictor(this)
        prediction = predictor.predict(metadata)
    } catch (Exception e) {
        echo "⚠️ ML prediction failed: ${e.message}. Using fallback heuristics."
        prediction = getFallbackPrediction(metadata)
    }

    echo '┌──────────────────────────────────────┐'
    echo '│         ML PREDICTIONS               │'
    echo '├──────────────────────────────────────┤'
    echo "│  CPU Usage       : ${prediction.cpu}%"
    echo "│  Memory          : ${prediction.memoryGb} GB"
    echo "│  Build Time      : ${prediction.timeMinutes} min"
    echo "│  Confidence      : ${prediction.confidence ?: 'low'}"
    echo "│  Method          : ${prediction.method ?: 'ml_prediction'}"
    echo '└──────────────────────────────────────┘'

    // ========================================
    // 2. Map to AWS Node
    // ========================================
    echo '\n🏷️ Selecting Best AWS EC2 Node...'

    def mapper = new LabelMapper()
    def label = mapper.getLabel(prediction.memoryGb)
    def instanceType = mapper.getInstanceType(prediction.memoryGb)
    def memoryForLabel = mapper.getMemoryForLabel(label)

    echo '┌──────────────────────────────────────┐'
    echo '│     🏆 RECOMMENDED AWS NODE          │'
    echo '├──────────────────────────────────────┤'
    echo "│  Jenkins Label   : ${label}"
    echo "│  AWS Instance    : ${instanceType}"
    echo "│  Instance Memory : ${memoryForLabel} GB"
    echo "│  Predicted Need  : ${prediction.memoryGb} GB"
    echo "│  Buffer          : +20% safety margin"
    echo '├──────────────────────────────────────┤'
    echo '│  WHY THIS NODE?                      │'
    echo "│  ${getReasoningText(prediction, metadata, label)}"
    echo '└──────────────────────────────────────┘'

    // ========================================
    // 3. Show all available nodes for comparison
    // ========================================
    echo '\n📋 All Available Nodes:'
    echo '┌──────────────┬─────────────────┬──────────┬───────────┐'
    echo '│ Label        │ Instance        │ Memory   │ Match     │'
    echo '├──────────────┼─────────────────┼──────────┼───────────┤'
    
    def allLabels = mapper.getAllLabels()
    allLabels.each { lbl ->
        def instType = mapper.getInstanceType(mapper.getMemoryForLabel(lbl).toDouble())
        def mem = mapper.getMemoryForLabel(lbl)
        def marker = (lbl == label) ? '  ✅ BEST' : ''
        echo "│ ${lbl.padRight(13)}│ ${instType.padRight(16)}│ ${(mem + ' GB').padRight(9)}│${marker.padRight(10)}│"
    }
    echo '└──────────────┴─────────────────┴──────────┴───────────┘'

    // ========================================
    // 4. Set Environment Variables
    // ========================================
    env.ML_SELECTED_LABEL = label
    env.ML_PREDICTED_MEMORY = prediction.memoryGb.toString()
    env.ML_PREDICTED_CPU = prediction.cpu.toString()
    env.ML_PREDICTED_TIME = prediction.timeMinutes.toString()
    env.ML_PROJECT_TYPE = (metadata.projectType ?: 'unknown').toString()
    env.ML_PREDICTION_CONFIDENCE = prediction.confidence ?: 'low'

    echo "\n✅ Node selection complete. Use label '${label}' for your build agent."

    // ========================================
    // Return Full Result
    // ========================================
    return [
        label: label,
        instanceType: instanceType,
        predictedMemoryGb: prediction.memoryGb,
        predictedCpu: prediction.cpu,
        predictedTimeMinutes: prediction.timeMinutes,
        confidence: prediction.confidence ?: 'low',
        projectType: metadata.projectType,
        metadata: metadata
    ]
}

/**
 * Generate human-readable reasoning for node selection.
 */
String getReasoningText(Map prediction, Map metadata, String label) {
    def reasons = []
    
    def projectType = metadata.projectType ?: 'unknown'
    reasons << "${projectType} project"
    
    if (prediction.memoryGb > 8) {
        reasons << "high memory (${prediction.memoryGb} GB)"
    } else if (prediction.memoryGb > 4) {
        reasons << "moderate memory (${prediction.memoryGb} GB)"
    } else {
        reasons << "low memory (${prediction.memoryGb} GB)"
    }
    
    if (metadata.hasE2ETests == 1) reasons << "has E2E tests"
    if (metadata.usesEmulator == 1) reasons << "uses emulator"
    if (metadata.hasDockerBuild == 1) reasons << "Docker build"
    if (metadata.isFirstBuild == 1) reasons << "first build (no cache)"
    
    return reasons.join(' | ')
}

/**
 * Fallback prediction when ML model fails.
 */
Map getFallbackPrediction(Map context) {
    def projectType = context.projectType ?: 'python'
    def filesChanged = context.filesChanged ?: 0

    def defaults = [
        'python': [cpu: 30.0, memoryGb: 2.0, timeMinutes: 5.0],
        'java': [cpu: 50.0, memoryGb: 4.0, timeMinutes: 10.0],
        'nodejs': [cpu: 40.0, memoryGb: 3.0, timeMinutes: 8.0],
        'react-native': [cpu: 70.0, memoryGb: 8.0, timeMinutes: 30.0],
        'android': [cpu: 75.0, memoryGb: 10.0, timeMinutes: 40.0],
        'ios': [cpu: 70.0, memoryGb: 8.0, timeMinutes: 35.0]
    ]

    def base = defaults[projectType] ?: defaults['python']
    def scale = Math.max(1.0, 1.0 + (filesChanged / 50.0) * 0.5)

    return [
        cpu: Math.min(100.0, base.cpu * scale).round(1),
        memoryGb: (base.memoryGb * scale).round(2),
        timeMinutes: (base.timeMinutes * scale).round(1),
        confidence: 'low',
        method: 'fallback_heuristic'
    ]
}
