import org.ml.nodeselection.GitAnalyzer
import org.ml.nodeselection.NodePredictor
import org.ml.nodeselection.LabelMapper

def call (Map config = [:]) {

    def buildType config.buildType ?: 'debug'
    def modelPath = config.modelPath ?: "$(env.JENKINS_HOME}/ml-models"

    echo 'ML Node Selection Starting'

    // Step 1: Analyze git changes

    def gitAnalyzer new GitAnalyzer(this)
    def gitMetrics gitAnalyzer.analyze()

    echo 'Code Analysis:'
    echo "Files Changed: $(gitMetrics.filesChanged)"
    echo "Lines Added: $(gitMetrics.linesAdded}"
    echo "Lines Deleted: $(gitMetrics.depsChanged}"
    echo "Branch: $(gitMetrics.branch)"

// Step 2: Make prediction

    def predictor= new NodePredictor(this)
    def prediction = predictor.predict([
    filesChanged: gitMetrics.filesChanged,
    linesAdded: gitMetrics.linesAdded,
    linesDeleted: gitMetrics.linesDeleted,
    depsChanged: gitMetrics.depsChanged,
    branch: gitMetrics.branch,
    buildType: buildType
    ])

    echo "ML Prediction: ${prediction}"
    echo "Predicted CPU: $(prediction.cpu)%"
    echo "Predicted Memory: $(prediction.memoryGb) GB"
    echo "Predicted Time: $(prediction.timeMinutes) min"

    def mapper = new LabelMapper()
    def label mappper.getLabel(prediction.memoryGb)
    def instanceType = mapper.getInstanceType(prediction.memoryGb)

    echo "Jenkins Label ${label}"
    echo "AWS Instance $(instanceType}"

    env.ML_SELECTED_LABEL = label
    env.ML_PREDICTED_MEMORY = prediction.memoryGb.toString()
    env.ML_PREDICTED_CPU = prediction.cpu.toString()
    env.ML_PREDICTED_TIME = prediction.timeMinutes.toString()

    return [
    label: label,
    instanceType: instanceType,
    predictedMemoryGb: prediction.memoryGb,
    predictedCpu: prediction.cpu,
    predictedTimeMinutes: prediction.timeMinutes,
    confidence: prediction.confidence,
    gitMetrics: gitMetrics
    ]
}
