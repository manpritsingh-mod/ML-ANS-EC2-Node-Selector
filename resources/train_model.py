#!/usr/bin/env python3
"""
Enhanced ML Model Training Script
==================================
Trains a RandomForest model on 27 features (30 columns - 3 targets).

Features include:
- Project context: project_type, repo_size_mb, is_monorepo
- Git metrics: files_changed, lines_added, lines_deleted, test_files_changed
- Pipeline config: has_unit_tests, has_e2e_tests, uses_emulator, etc.
- Cache state: is_first_build, cache_available, is_clean_build
- Time context: time_of_day_hour

Targets:
- cpu_avg_pct: Average CPU usage (%)
- memory_gb: Peak memory usage (GB)
- build_time_min: Total build time (minutes)
"""

import argparse
import os
import sys
import pandas as pd
import joblib
import numpy as np
from sklearn.ensemble import RandomForestRegressor
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.metrics import mean_absolute_error, r2_score


# =============================================================================
# FEATURE DEFINITIONS
# =============================================================================

# All 27 input features (must match training_features.csv columns minus targets)
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

# Target columns
TARGET_COLUMNS = ['cpu_avg_pct', 'memory_gb', 'build_time_min']


# =============================================================================
# DATA LOADING
# =============================================================================

def load_data(csv_path):
    """Load and validate the enhanced training dataset."""
    print(f"Loading dataset: {csv_path}")
    df = pd.read_csv(csv_path)
    
    print(f"  Total records: {len(df)}")
    print(f"  Total columns: {len(df.columns)}")
    
    # Check for required columns
    missing_features = [col for col in FEATURE_COLUMNS if col not in df.columns]
    if missing_features:
        raise ValueError(f"Missing feature columns: {missing_features}")
    
    missing_targets = [col for col in TARGET_COLUMNS if col not in df.columns]
    if missing_targets:
        raise ValueError(f"Missing target columns: {missing_targets}")
    
    # Filter to successful builds only if status column exists
    if 'status' in df.columns:
        original_len = len(df)
        df = df[df['status'].isin(['success', 'SUCCESS'])]
        print(f"  Filtered to successful builds: {len(df)}/{original_len}")
    
    if len(df) < 50:
        raise ValueError(f"Not enough data to train: {len(df)} records (need 50+)")
    
    # Extract features and targets
    X = df[FEATURE_COLUMNS].fillna(0)
    y = df[TARGET_COLUMNS].fillna(0)
    
    print(f"\n  Features shape: {X.shape}")
    print(f"  Targets shape: {y.shape}")
    
    return X, y


# =============================================================================
# MODEL TRAINING
# =============================================================================

def train_model(X, y):
    """Train RandomForest model with cross-validation."""
    print(f"\n{'='*60}")
    print("Training Enhanced ML Model")
    print(f"{'='*60}")
    print(f"Training samples: {len(X)}")
    print(f"Features: {len(X.columns)}")
    
    # Split data: 80% train, 20% test
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42
    )
    print(f"Train/Test split: {len(X_train)}/{len(X_test)}")
    
    # Create model with tuned hyperparameters
    model = RandomForestRegressor(
        n_estimators=150,      # More trees for better accuracy
        max_depth=15,          # Deeper trees for complex patterns
        min_samples_split=5,   # Prevent overfitting
        min_samples_leaf=2,
        max_features='sqrt',   # Use sqrt of features per split
        random_state=42,
        n_jobs=-1,             # Use all CPU cores
    )
    
    # Train model
    print("\nTraining Random Forest...")
    model.fit(X_train, y_train)
    
    # Evaluate on test set
    predictions = model.predict(X_test)
    
    print(f"\n{'='*60}")
    print("Model Performance (Test Set)")
    print(f"{'='*60}")
    
    overall_r2 = r2_score(y_test, predictions)
    overall_mae = mean_absolute_error(y_test, predictions)
    print(f"\nOverall R² Score:  {overall_r2:.4f}")
    print(f"Overall MAE:       {overall_mae:.4f}")
    
    print("\nPer-Target Metrics:")
    for i, col in enumerate(TARGET_COLUMNS):
        y_true = y_test.iloc[:, i]
        y_pred = predictions[:, i]
        r2 = r2_score(y_true, y_pred)
        mae = mean_absolute_error(y_true, y_pred)
        print(f"  {col:15s} → R²: {r2:.4f}, MAE: {mae:.4f}")
    
    # Feature importance
    print(f"\n{'='*60}")
    print("Feature Importance (Top 10)")
    print(f"{'='*60}")
    importance = dict(zip(FEATURE_COLUMNS, model.feature_importances_))
    sorted_imp = sorted(importance.items(), key=lambda x: -x[1])
    for i, (feature, imp) in enumerate(sorted_imp[:10], 1):
        print(f"  {i:2d}. {feature:25s} {imp:.4f}")
    
    # Cross-validation score
    print(f"\n{'='*60}")
    print("Cross-Validation (5-fold)")
    print(f"{'='*60}")
    
    # Flatten y for CV scoring (use build_time_min as primary metric)
    cv_scores = cross_val_score(model, X, y['build_time_min'], cv=5, scoring='r2')
    print(f"  Build Time R² scores: {cv_scores}")
    print(f"  Mean CV R²: {cv_scores.mean():.4f} (+/- {cv_scores.std() * 2:.4f})")
    
    return model, {
        'r2_score': overall_r2,
        'mae': overall_mae,
        'cv_mean': cv_scores.mean(),
        'feature_importance': sorted_imp
    }


# =============================================================================
# SAVE MODEL AND METADATA
# =============================================================================

def save_model(model, metrics, model_dir, feature_list):
    """Save trained model and metadata."""
    os.makedirs(model_dir, exist_ok=True)
    
    # Save model
    model_path = os.path.join(model_dir, 'model.pkl')
    joblib.dump(model, model_path)
    print(f"\n✅ Model saved: {model_path}")
    
    # Save feature list for predict.py to use
    feature_path = os.path.join(model_dir, 'features.json')
    import json
    with open(feature_path, 'w') as f:
        json.dump({
            'features': feature_list,
            'targets': TARGET_COLUMNS,
            'metrics': {
                'r2_score': float(metrics['r2_score']),
                'mae': float(metrics['mae']),
                'cv_mean': float(metrics['cv_mean']),
            }
        }, f, indent=2)
    print(f"✅ Feature list saved: {feature_path}")
    
    return model_path


# =============================================================================
# MAIN
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description='Train Enhanced ML Model for Jenkins CI Resource Prediction'
    )
    parser.add_argument(
        '--data-path', 
        required=True, 
        help='Path to enhanced training CSV (training_features.csv)'
    )
    parser.add_argument(
        '--model-path', 
        required=True, 
        help='Directory to save trained model'
    )
    args = parser.parse_args()
    
    # Validate paths
    if not os.path.exists(args.data_path):
        print(f"❌ Dataset file not found: {args.data_path}", file=sys.stderr)
        sys.exit(1)
    
    try:
        # Load data
        X, y = load_data(args.data_path)
        
        # Train model
        model, metrics = train_model(X, y)
        
        # Save model and metadata
        save_model(model, metrics, args.model_path, FEATURE_COLUMNS)
        
        # Summary
        print(f"\n{'='*60}")
        print("Training Complete!")
        print(f"{'='*60}")
        print(f"  R² Score: {metrics['r2_score']:.4f}")
        print(f"  MAE:      {metrics['mae']:.4f}")
        print(f"  CV Mean:  {metrics['cv_mean']:.4f}")
        
        if metrics['r2_score'] >= 0.75:
            print("\n✅ Model meets target accuracy (R² >= 0.75)")
        else:
            print(f"\n⚠️ Model below target accuracy (R² = {metrics['r2_score']:.4f} < 0.75)")
            print("   Consider collecting more training data or tuning hyperparameters")
        
    except Exception as e:
        print(f"❌ Training failed: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()