#!/usr/bin/env python3
import argparse
import os
import sys
import pandas as pd
import joblib
from sklearn.ensemble import RandomForestRegressor
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_absolute_error, r2_score

def load_data(csv_path):
    """Load and preprocess the training dataset."""
    print(f"Loading dataset: {csv_path}")
    df = pd.read_csv(csv_path)

    # Keep only successful builds
    if "status" in df.columns:
        df = df[df["status"] == "SUCCESS"]

    if len(df) < 10:
        raise ValueError("Not enough data to train the model")

    # Feature engineering
    df["net_lines"] = df["lines_added"] - df["lines_deleted"]
    df["total_changes"] = df["lines_added"] + df["lines_deleted"]
    df["code_density"] = df["total_changes"] / (df["files_changed"] + 1)
    df["is_main"] = df["branch"].isin(["main", "master"]).astype(int)
    df["is_release"] = df["build_type"].isin(["release", "prodRelease"]).astype(int)

    # Input features
    X = df[
        [
            "files_changed",
            "lines_added",
            "lines_deleted",
            "net_lines",
            "total_changes",
            "deps_changed",
            "is_main",
            "is_release",
            "code_density",
        ]
    ].fillna(0)

    # Target values
    y = pd.DataFrame()
    y["cpu_avg"] = df["cpu_avg"]
    y["memory_gb"] = df["memory_avg_mb"] / 1024
    y["time_minutes"] = df["build_time_sec"] / 60

    return X, y

def train_model(X, y):
    """Train a RandomForest model for resource prediction."""
    print(f"Training model with {len(X)} records")

    # Split data: 80% train, 20% test
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42
    )

    model = RandomForestRegressor(
        n_estimators=100,  # Number of decision trees
        max_depth=10,  # Each tree can make at most 10 decisions from root to leaf
        random_state=42,
        n_jobs=-1,  # Use all available CPU cores
    )

    model.fit(X_train, y_train)
    predictions = model.predict(X_test)

    print("\nModel Performance:")
    print(f"Overall MAE: {mean_absolute_error(y_test, predictions):.3f}")
    print(f"Overall R²: {r2_score(y_test, predictions):.2f}")

    print("\nPer Target Accuracy:")
    for i, col in enumerate(y.columns):
        print(
            f"  {col} → MAE: {mean_absolute_error(y_test.iloc[:, i], predictions[:, i]):.3f}, "
            f"R²: {r2_score(y_test.iloc[:, i], predictions[:, i]):.2f}"
        )

    print("\nTop Feature Importance:")
    importance = dict(zip(X.columns, model.feature_importances_))
    for k, v in sorted(importance.items(), key=lambda x: -x[1])[:5]:
        print(f"  {k}: {v:.3f}")

    return model

def main():
    parser = argparse.ArgumentParser(description="Train ML model for Jenkins CI")
    parser.add_argument(
        "--data-path", required=True, help="Path to CSV training data"
    )
    parser.add_argument(
        "--model-path", required=True, help="Directory to save model"
    )
    args = parser.parse_args()

    if not os.path.exists(args.data_path):
        print("Dataset file not found", file=sys.stderr)
        sys.exit(1)

    os.makedirs(args.model_path, exist_ok=True)

    try:
        X, y = load_data(args.data_path)
        model = train_model(X, y)

        model_file = os.path.join(args.model_path, "model.pkl")
        joblib.dump(model, model_file)
        print(f"\nModel saved successfully at: {model_file}")

    except Exception as e:
        print(f"Training failed: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()