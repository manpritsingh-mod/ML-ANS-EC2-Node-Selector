#!/usr/bin/env python3
"""
Enhanced ML Prediction Script
==============================
Predicts CPU, Memory, and Build Time using 27 features.

Input: JSON file with build context (from PipelineAnalyzer.groovy)
Output: JSON with predictions

Compatible with both:
- Basic context (git metrics only) - backward compatible
- Enhanced context (full pipeline analysis) - full accuracy
"""

import argparse
import json
import os
import sys
import joblib
import numpy as np


# =============================================================================
# FEATURE DEFINITIONS (must match train_model.py)
# =============================================================================

FEATURE_COLUMNS = [
    # Project context (3)
    'project_type',
    'repo_size_mb', 
    'is_monorepo',
    
    # Branch/Build context (3)
    'branch_type',
    'build_type',
    'environment',
    
    # Git metrics (7)
    'files_changed',
    'lines_added',
    'lines_deleted',
    'source_files_pct',
    'deps_file_changed',
    'dependency_count',
    'test_files_changed',
    
    # Pipeline configuration (9)
    'stages_count',
    'has_build_stage',
    'has_unit_tests',
    'has_integration_tests',
    'has_e2e_tests',
    'has_deploy_stage',
    'has_docker_build',
    'uses_emulator',
    'parallel_stages',
    'has_artifact_publish',
    
    # Cache/Build state (3)
    'is_first_build',
    'cache_available',
    'is_clean_build',
    
    # Time context (1)
    'time_of_day_hour',
]

# Project type mapping
PROJECT_TYPES = {
    'python': 0,
    'java': 1,
    'nodejs': 2,
    'node': 2,
    'react-native': 3,
    'reactnative': 3,
    'android': 4,
    'ios': 5,
    'unknown': 0,  # Default to Python-like
}

# Branch type mapping
BRANCH_TYPES = {
    'feature': 0,
    'develop': 1,
    'development': 1,
    'main': 2,
    'master': 2,
    'hotfix': 3,
    'release': 4,
}


# =============================================================================
# FEATURE ENGINEERING
# =============================================================================

def engineer_features(context):
    """
    Transform raw build context into 27 ML features.
    
    Handles both:
    - Basic context (backward compatible with old API)
    - Enhanced context (full pipeline analysis)
    """
    
    features = {}
    
    # === Project Context ===
    project_type_str = context.get('projectType', context.get('project_type', 'unknown'))
    features['project_type'] = PROJECT_TYPES.get(project_type_str.lower(), 0)
    features['repo_size_mb'] = float(context.get('repoSizeMb', context.get('repo_size_mb', 100)))
    features['is_monorepo'] = int(context.get('isMonorepo', context.get('is_monorepo', 0)))
    
    # === Branch/Build Context ===
    branch = context.get('branch', 'develop')
    branch_type_val = context.get('branchType', context.get('branch_type', None))
    if isinstance(branch_type_val, int):
        # PipelineAnalyzer sends branchType as int (0-4) — use directly
        features['branch_type'] = branch_type_val
    elif branch_type_val and isinstance(branch_type_val, str):
        features['branch_type'] = BRANCH_TYPES.get(branch_type_val.lower(), 0)
    else:
        # Infer from branch name
        if 'feature' in branch.lower():
            features['branch_type'] = 0
        elif branch.lower() in ['develop', 'development']:
            features['branch_type'] = 1
        elif branch.lower() in ['main', 'master']:
            features['branch_type'] = 2
        elif 'hotfix' in branch.lower():
            features['branch_type'] = 3
        elif 'release' in branch.lower():
            features['branch_type'] = 4
        else:
            features['branch_type'] = 0  # Default feature
    
    build_type = context.get('buildType', 'debug')
    features['build_type'] = 1 if build_type.lower() in ['release', 'prodrelease'] else 0
    
    env = context.get('environment', 'development')
    if env.lower() in ['dev', 'development']:
        features['environment'] = 0
    elif env.lower() == 'staging':
        features['environment'] = 1
    else:
        features['environment'] = 2  # production
    
    # === Git Metrics ===
    features['files_changed'] = int(context.get('filesChanged', context.get('files_changed', 5)))
    features['lines_added'] = int(context.get('linesAdded', context.get('lines_added', 100)))
    features['lines_deleted'] = int(context.get('linesDeleted', context.get('lines_deleted', 20)))
    features['source_files_pct'] = float(context.get('sourceFilesPct', context.get('source_files_pct', 0.8)))
    features['deps_file_changed'] = int(context.get('depsChanged', context.get('deps_file_changed', 0)))
    features['dependency_count'] = int(context.get('dependencyCount', context.get('dependency_count', 50)))
    features['test_files_changed'] = int(context.get('testFilesChanged', context.get('test_files_changed', 0)))
    
    # === Pipeline Configuration ===
    features['stages_count'] = int(context.get('stagesCount', context.get('stages_count', 3)))
    features['has_build_stage'] = int(context.get('hasBuildStage', context.get('has_build_stage', 1)))
    features['has_unit_tests'] = int(context.get('hasUnitTests', context.get('has_unit_tests', 1)))
    features['has_integration_tests'] = int(context.get('hasIntegrationTests', context.get('has_integration_tests', 0)))
    features['has_e2e_tests'] = int(context.get('hasE2ETests', context.get('has_e2e_tests', 0)))
    features['has_deploy_stage'] = int(context.get('hasDeployStage', context.get('has_deploy_stage', 0)))
    features['has_docker_build'] = int(context.get('hasDockerBuild', context.get('has_docker_build', 0)))
    features['uses_emulator'] = int(context.get('usesEmulator', context.get('uses_emulator', 0)))
    features['parallel_stages'] = int(context.get('parallelStages', context.get('parallel_stages', 0)))
    features['has_artifact_publish'] = int(context.get('hasArtifactPublish', context.get('has_artifact_publish', 0)))
    
    # === Cache/Build State ===
    features['is_first_build'] = int(context.get('isFirstBuild', context.get('is_first_build', 0)))
    features['cache_available'] = int(context.get('cacheAvailable', context.get('cache_available', 1)))
    features['is_clean_build'] = int(context.get('isCleanBuild', context.get('is_clean_build', 0)))
    
    # === Time Context ===
    from datetime import datetime
    hour = context.get('timeOfDayHour', context.get('time_of_day_hour', None))
    if hour is None:
        hour = datetime.now().hour
    features['time_of_day_hour'] = int(hour)
    
    return features


# =============================================================================
# PREDICTION
# =============================================================================

def predict_resources(features, model_path):
    """Run prediction using trained model."""
    
    if not os.path.exists(model_path):
        raise FileNotFoundError(f"Model not found: {model_path}")
    
    # Load model
    model = joblib.load(model_path)
    
    # Build feature vector in correct order
    X = np.array([[features.get(col, 0) for col in FEATURE_COLUMNS]])
    
    # Predict
    prediction = model.predict(X)[0]
    
    # Extract predictions
    cpu_pct = max(10, min(100, float(prediction[0])))      # Clamp 10-100%
    memory_gb = max(0.5, float(prediction[1]))             # Min 0.5 GB
    time_min = max(1, float(prediction[2]))                # Min 1 minute
    
    return {
        'cpu': round(cpu_pct, 1),
        'memoryGb': round(memory_gb, 2),
        'timeMinutes': round(time_min, 1),
        'method': 'ml_enhanced_prediction',
        'features_used': len(FEATURE_COLUMNS)
    }


def get_confidence(features):
    """Estimate prediction confidence based on input completeness."""
    
    # Check if critical features are provided (not default values)
    critical_features = [
        ('project_type', 0),
        ('has_e2e_tests', 0),
        ('uses_emulator', 0),
        ('stages_count', 3),
        ('is_first_build', 0),
    ]
    
    provided_count = 0
    for feat, default in critical_features:
        if features.get(feat) != default:
            provided_count += 1
    
    # Base confidence from feature coverage
    if provided_count >= 4:
        return 'high'
    elif provided_count >= 2:
        return 'medium'
    else:
        return 'low'


# =============================================================================
# MAIN
# =============================================================================

def main():
    parser = argparse.ArgumentParser(description='Enhanced ML Resource Prediction')
    parser.add_argument('--input', required=True, help='Build context JSON file')
    parser.add_argument('--model', required=True, help='Path to trained model.pkl')
    args = parser.parse_args()
    
    try:
        # Load input context
        with open(args.input, 'r') as f:
            context = json.load(f)
    except Exception as e:
        result = {'error': f'Invalid input JSON: {e}'}
        print(json.dumps(result), file=sys.stderr)
        sys.exit(1)
    
    try:
        # Engineer features
        features = engineer_features(context)
        
        # Predict resources
        result = predict_resources(features, args.model)
        
        # Add confidence score
        result['confidence'] = get_confidence(features)
        
        # Add debug info if requested
        if context.get('debug', False):
            result['features'] = features
        
        # Output JSON
        print(json.dumps(result))
        
    except Exception as e:
        result = {'error': str(e)}
        print(json.dumps(result), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()