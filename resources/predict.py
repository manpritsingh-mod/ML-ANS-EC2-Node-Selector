#!/usr/bin/env python3
import argparse
import json
import os
import sys
import joblib
import numpy as np


def engineer_features(context):
    """Extract and engineer features from the build context."""
    files = context.get('filesChanged', 0)
    lines_added = context.get('linesAdded', 0)
    lines_deleted = context.get('linesDeleted', 0)
    deps = context.get('depsChanged', 0)
    branch = context.get('branch', 'main')
    build_type = context.get('buildType', 'debug')

    return {
        'files_changed': files,
        'lines_added': lines_added,
        'lines_deleted': lines_deleted,
        'net_lines': lines_added - lines_deleted,
        'total_changes': lines_added + lines_deleted,
        'deps_changed': deps,
        'is_main': 1 if branch in ['main', 'master', 'develop'] else 0,
        'is_release': 1 if build_type in ['release', 'prodRelease'] else 0,
        'code_density': (lines_added + lines_deleted) / max(files, 1)
    }


def predict_with_model(features, model_path):
    """Predict CPU, Memory, Time using trained model."""
    if not os.path.exists(model_path):
        raise FileNotFoundError(f"Model not found: {model_path}")

    model = joblib.load(model_path)

    feature_order = [
        'files_changed',
        'lines_added',
        'lines_deleted',
        'net_lines',
        'total_changes',
        'deps_changed',
        'is_main',
        'is_release',
        'code_density'
    ]

    for f in feature_order:
        if f not in features:
            raise ValueError(f"Missing feature: {f}")

    X = np.array([[features[f] for f in feature_order]])
    prediction = model.predict(X)[0]

    return {
        'cpu': round(float(prediction[0]), 2),
        'memoryGb': round(float(prediction[1]), 2),
        'timeMinutes': round(float(prediction[2]), 2),
        'method': 'ml_prediction'
    }


def main():
    parser = argparse.ArgumentParser(description='ML Resource Prediction')
    parser.add_argument('--input', required=True, help='Build context JSON file')
    parser.add_argument('--model', required=True, help='Path to trained model.pkl')
    args = parser.parse_args()

    try:
        with open(args.input, 'r') as f:
            context = json.load(f)
    except Exception as e:
        print(json.dumps({'error': f'Invalid input JSON: {e}'}), file=sys.stderr)
        sys.exit(1)

    try:
        features = engineer_features(context)
        result = predict_with_model(features, args.model)
        print(json.dumps(result))
    except Exception as e:
        print(json.dumps({'error': str(e)}), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()