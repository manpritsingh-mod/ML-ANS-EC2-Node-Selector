"""
Enhanced ML Training Data Generator
====================================

Generates realistic CI/CD build metrics for ML model training by:
1. Using Kaggle CI/CD pipeline logs as base patterns
2. Applying industry-researched correlations for resource usage
3. Covering all project types and pipeline configurations

Author: ML-Node-Selector Team
Based on: AI-Driven CI/CD Pipeline Logs Dataset (Kaggle)
"""

import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import random
import hashlib
import os

# Set random seed for reproducibility
np.random.seed(42)
random.seed(42)


# =============================================================================
# CONFIGURATION: Industry-researched realistic value ranges
# =============================================================================

PROJECT_TYPES = {
    0: 'python',
    1: 'java',
    2: 'nodejs', 
    3: 'react-native',
    4: 'android',
    5: 'ios'
}

PROJECT_TYPE_WEIGHTS = [0.25, 0.20, 0.20, 0.15, 0.12, 0.08]

BRANCH_TYPE_MAP = {
    0: 'feature',
    1: 'develop',
    2: 'main',
    3: 'hotfix',
    4: 'release'
}

# Resource profiles by project type and pipeline config
# Format: (min, max) for each metric
RESOURCE_PROFILES = {
    'python': {
        'base': {'memory_gb': (0.5, 2), 'cpu_pct': (15, 40), 'time_min': (2, 8)},
        'unit_tests': {'memory_gb': (1, 3), 'cpu_pct': (30, 60), 'time_min': (3, 12)},
        'integration': {'memory_gb': (2, 5), 'cpu_pct': (40, 75), 'time_min': (8, 25)},
        'deps_no_cache': {'memory_gb': (2, 4), 'cpu_pct': (25, 50), 'time_min': (5, 30)},
        'docker': {'memory_gb': (3, 6), 'cpu_pct': (50, 80), 'time_min': (10, 30)},
    },
    'java': {
        'base': {'memory_gb': (2, 4), 'cpu_pct': (40, 70), 'time_min': (5, 15)},
        'unit_tests': {'memory_gb': (3, 6), 'cpu_pct': (50, 80), 'time_min': (10, 25)},
        'integration': {'memory_gb': (4, 8), 'cpu_pct': (60, 90), 'time_min': (15, 45)},
        'deps_no_cache': {'memory_gb': (3, 5), 'cpu_pct': (45, 65), 'time_min': (8, 25)},
        'docker': {'memory_gb': (4, 8), 'cpu_pct': (55, 85), 'time_min': (15, 35)},
    },
    'nodejs': {
        'base': {'memory_gb': (1, 3), 'cpu_pct': (20, 50), 'time_min': (1, 5)},
        'unit_tests': {'memory_gb': (1.5, 4), 'cpu_pct': (30, 65), 'time_min': (3, 10)},
        'integration': {'memory_gb': (2, 5), 'cpu_pct': (40, 75), 'time_min': (5, 20)},
        'deps_no_cache': {'memory_gb': (2, 4), 'cpu_pct': (30, 55), 'time_min': (3, 25)},
        'docker': {'memory_gb': (2, 5), 'cpu_pct': (45, 75), 'time_min': (5, 20)},
        'e2e': {'memory_gb': (4, 8), 'cpu_pct': (60, 85), 'time_min': (15, 40)},
    },
    'react-native': {
        'base': {'memory_gb': (4, 8), 'cpu_pct': (50, 80), 'time_min': (10, 25)},
        'unit_tests': {'memory_gb': (5, 10), 'cpu_pct': (55, 85), 'time_min': (15, 35)},
        'integration': {'memory_gb': (6, 12), 'cpu_pct': (60, 90), 'time_min': (20, 50)},
        'e2e': {'memory_gb': (8, 16), 'cpu_pct': (70, 95), 'time_min': (30, 90)},
        'emulator': {'memory_gb': (10, 18), 'cpu_pct': (75, 95), 'time_min': (40, 100)},
        'release': {'memory_gb': (8, 14), 'cpu_pct': (65, 90), 'time_min': (25, 50)},
    },
    'android': {
        'base': {'memory_gb': (4, 8), 'cpu_pct': (55, 85), 'time_min': (8, 20)},
        'unit_tests': {'memory_gb': (5, 10), 'cpu_pct': (60, 88), 'time_min': (12, 30)},
        'integration': {'memory_gb': (6, 12), 'cpu_pct': (65, 92), 'time_min': (18, 45)},
        'e2e': {'memory_gb': (10, 20), 'cpu_pct': (75, 98), 'time_min': (35, 90)},
        'emulator': {'memory_gb': (12, 24), 'cpu_pct': (80, 98), 'time_min': (45, 120)},
        'release': {'memory_gb': (8, 16), 'cpu_pct': (70, 95), 'time_min': (20, 40)},
    },
    'ios': {
        'base': {'memory_gb': (4, 10), 'cpu_pct': (50, 80), 'time_min': (10, 30)},
        'unit_tests': {'memory_gb': (5, 12), 'cpu_pct': (55, 85), 'time_min': (15, 40)},
        'integration': {'memory_gb': (6, 14), 'cpu_pct': (60, 90), 'time_min': (20, 55)},
        'e2e': {'memory_gb': (8, 18), 'cpu_pct': (70, 95), 'time_min': (30, 80)},
        'release': {'memory_gb': (8, 16), 'cpu_pct': (65, 90), 'time_min': (20, 45)},
    },
}

# Git metrics ranges by project type (realistic based on typical repos)
GIT_METRICS = {
    'python': {'files': (1, 40), 'lines_add': (5, 800), 'deps': 50},
    'java': {'files': (1, 60), 'lines_add': (10, 1200), 'deps': 80},
    'nodejs': {'files': (1, 50), 'lines_add': (5, 1000), 'deps': 150},
    'react-native': {'files': (1, 80), 'lines_add': (10, 1500), 'deps': 200},
    'android': {'files': (1, 100), 'lines_add': (10, 2000), 'deps': 100},
    'ios': {'files': (1, 60), 'lines_add': (10, 1200), 'deps': 60},
}

REPO_SIZES = {
    'python': (10, 500),      # MB
    'java': (50, 2000),
    'nodejs': (10, 800),
    'react-native': (100, 3000),
    'android': (100, 5000),
    'ios': (100, 3000),
}


# =============================================================================
# KAGGLE DATA PARSING
# =============================================================================

def load_kaggle_data(kaggle_path):
    """Load and parse Kaggle CI/CD logs for pipeline patterns."""
    try:
        df = pd.read_csv(kaggle_path)
        print(f"✅ Loaded Kaggle data: {len(df)} records")
        return df
    except FileNotFoundError:
        print(f"⚠️ Kaggle file not found at {kaggle_path}, using synthetic patterns")
        return None


def extract_pipeline_patterns(kaggle_df):
    """Extract realistic pipeline patterns from Kaggle data."""
    if kaggle_df is None:
        return None
    
    patterns = {
        'stages': kaggle_df['stage_name'].unique().tolist(),
        'jobs': kaggle_df['job_name'].unique().tolist(),
        'tasks': kaggle_df['task_name'].unique().tolist(),
        'status_rates': kaggle_df['status'].value_counts(normalize=True).to_dict(),
        'environments': kaggle_df['environment'].dropna().unique().tolist(),
        'branches': kaggle_df['branch'].str.extract(r'(branch_)(\w+)')[1].dropna().unique().tolist()[:20],
    }
    print(f"✅ Extracted patterns: {len(patterns['stages'])} stages, {len(patterns['jobs'])} jobs")
    return patterns


# =============================================================================
# DATA GENERATION
# =============================================================================

def generate_branch_name(branch_type_idx):
    """Generate realistic branch names."""
    branch_type = BRANCH_TYPE_MAP[branch_type_idx]
    suffixes = ['auth', 'login', 'api', 'ui', 'db', 'test', 'fix', 'perf', 
                'mobile', 'cache', 'analytics', 'search', 'payment']
    
    if branch_type == 'feature':
        return f"feature/{random.choice(suffixes)}"
    elif branch_type == 'develop':
        return 'develop'
    elif branch_type == 'main':
        return random.choice(['main', 'master'])
    elif branch_type == 'hotfix':
        return f"hotfix/{random.choice(['bug', 'fix', 'urgent', 'patch'])}"
    elif branch_type == 'release':
        return f"release/v{random.randint(1,5)}.{random.randint(0,9)}.{random.randint(0,20)}"


def calculate_resources(project_type_name, pipeline_config, cache_available, is_first_build, is_release, is_clean_build=False, is_monorepo=False):
    """Calculate resource requirements based on project and pipeline config."""
    
    profile = RESOURCE_PROFILES[project_type_name]
    
    # Start with base profile
    memory = profile['base']['memory_gb']
    cpu = profile['base']['cpu_pct']
    time = profile['base']['time_min']
    
    # Add resource for each pipeline stage
    pipeline_additions = []
    
    if pipeline_config['has_unit_tests']:
        pipeline_additions.append('unit_tests')
    if pipeline_config['has_integration_tests']:
        pipeline_additions.append('integration')
    if pipeline_config['has_e2e_tests'] and 'e2e' in profile:
        pipeline_additions.append('e2e')
    if pipeline_config['uses_emulator'] and 'emulator' in profile:
        pipeline_additions.append('emulator')
    if pipeline_config['has_docker_build'] and 'docker' in profile:
        pipeline_additions.append('docker')
    
    # Take the most resource-intensive profile
    for addition in pipeline_additions:
        if addition in profile:
            add_profile = profile[addition]
            memory = (max(memory[0], add_profile['memory_gb'][0]), 
                     max(memory[1], add_profile['memory_gb'][1]))
            cpu = (max(cpu[0], add_profile['cpu_pct'][0]),
                  max(cpu[1], add_profile['cpu_pct'][1]))
            time = (max(time[0], add_profile['time_min'][0]),
                   time[1] + add_profile['time_min'][1] * 0.5)  # Stages add time
    
    # Apply modifiers
    memory_val = random.uniform(memory[0], memory[1])
    cpu_val = random.uniform(cpu[0], cpu[1])
    time_val = random.uniform(time[0], time[1])
    
    # No cache = slower
    if not cache_available:
        if 'deps_no_cache' in profile:
            nc = profile['deps_no_cache']
            time_val = max(time_val, random.uniform(nc['time_min'][0], nc['time_min'][1]))
        time_val *= random.uniform(1.5, 2.5)
    
    # First build = even slower
    if is_first_build:
        time_val *= random.uniform(1.3, 2.0)
        memory_val *= random.uniform(1.1, 1.3)
    
    # CLEAN BUILD = full rebuild, significantly slower
    if is_clean_build:
        time_val *= random.uniform(1.5, 2.5)  # Clean builds take 1.5-2.5x longer
        memory_val *= random.uniform(1.1, 1.2)
        cpu_val = min(100, cpu_val * 1.1)
    
    # MONOREPO = more projects to potentially build
    if is_monorepo:
        time_val *= random.uniform(1.2, 1.8)  # Monorepos take longer
        memory_val *= random.uniform(1.1, 1.4)
    
    # Release build = optimization overhead
    if is_release:
        if 'release' in profile:
            rel = profile['release']
            memory_val = max(memory_val, random.uniform(rel['memory_gb'][0], rel['memory_gb'][1]))
            cpu_val = max(cpu_val, random.uniform(rel['cpu_pct'][0], rel['cpu_pct'][1]))
        time_val *= random.uniform(1.3, 1.6)
        cpu_val = min(100, cpu_val * 1.15)
    
    return {
        'memory_gb': round(memory_val, 2),
        'cpu_avg_pct': round(cpu_val, 1),
        'build_time_min': round(time_val, 1)
    }


def generate_pipeline_config(project_type_name, is_release):
    """Generate realistic pipeline configuration for project type."""
    
    # Base pipeline probabilities by project type
    configs = {
        'python': {
            'has_unit_tests': 0.85,
            'has_integration_tests': 0.45,
            'has_e2e_tests': 0.10,
            'has_docker_build': 0.35,
            'uses_emulator': 0.0,
        },
        'java': {
            'has_unit_tests': 0.90,
            'has_integration_tests': 0.55,
            'has_e2e_tests': 0.15,
            'has_docker_build': 0.40,
            'uses_emulator': 0.0,
        },
        'nodejs': {
            'has_unit_tests': 0.80,
            'has_integration_tests': 0.40,
            'has_e2e_tests': 0.25,
            'has_docker_build': 0.30,
            'uses_emulator': 0.0,
        },
        'react-native': {
            'has_unit_tests': 0.75,
            'has_integration_tests': 0.35,
            'has_e2e_tests': 0.45,
            'has_docker_build': 0.15,
            'uses_emulator': 0.55,
        },
        'android': {
            'has_unit_tests': 0.85,
            'has_integration_tests': 0.45,
            'has_e2e_tests': 0.50,
            'has_docker_build': 0.10,
            'uses_emulator': 0.60,
        },
        'ios': {
            'has_unit_tests': 0.80,
            'has_integration_tests': 0.40,
            'has_e2e_tests': 0.40,
            'has_docker_build': 0.0,
            'uses_emulator': 0.50,  # Simulator
        },
    }
    
    proj_config = configs[project_type_name]
    
    # Higher probabilities for release builds
    release_multiplier = 1.3 if is_release else 1.0
    
    config = {
        'has_build_stage': 1,  # Always have build
        'has_unit_tests': 1 if random.random() < min(1, proj_config['has_unit_tests'] * release_multiplier) else 0,
        'has_integration_tests': 1 if random.random() < min(1, proj_config['has_integration_tests'] * release_multiplier) else 0,
        'has_e2e_tests': 1 if random.random() < min(1, proj_config['has_e2e_tests'] * release_multiplier) else 0,
        'has_deploy_stage': 1 if is_release or random.random() < 0.3 else 0,
        'has_docker_build': 1 if random.random() < proj_config['has_docker_build'] else 0,
        'uses_emulator': 1 if random.random() < proj_config['uses_emulator'] else 0,
        'parallel_stages': random.choice([1, 1, 2, 2, 3]) if is_release else random.choice([1, 1, 1, 2]),
    }
    
    # Count stages
    config['stages_count'] = sum([
        config['has_build_stage'],
        config['has_unit_tests'],
        config['has_integration_tests'],
        config['has_e2e_tests'],
        config['has_deploy_stage'],
    ])
    
    return config


def generate_git_metrics(project_type_name, is_small_change, has_tests):
    """Generate realistic git metrics including test files changed."""
    
    metrics = GIT_METRICS[project_type_name]
    
    if is_small_change:
        files = random.randint(1, 5)
        lines_add = random.randint(5, 100)
    else:
        files = random.randint(metrics['files'][0], metrics['files'][1])
        lines_add = random.randint(metrics['lines_add'][0], metrics['lines_add'][1])
    
    # Lines deleted typically less than added
    lines_del = int(lines_add * random.uniform(0.1, 0.5))
    
    # Source files percentage
    source_pct = random.uniform(0.5, 0.95)
    
    # Deps file changed?
    deps_changed = 1 if random.random() < 0.2 else 0
    
    # Number of dependencies
    deps_count = int(metrics['deps'] * random.uniform(0.7, 1.3))
    
    # TEST FILES CHANGED - correlates with test duration
    # If has tests and files changed, some are likely test files
    if has_tests and files > 1:
        # ~20-40% of changed files are test files
        test_files = max(0, int(files * random.uniform(0.15, 0.45)))
    else:
        test_files = 0
    
    return {
        'files_changed': files,
        'lines_added': lines_add,
        'lines_deleted': lines_del,
        'source_files_pct': round(source_pct, 2),
        'deps_file_changed': deps_changed,
        'dependency_count': deps_count,
        'test_files_changed': test_files,  # NEW
    }


def generate_single_record(record_id, base_timestamp, kaggle_patterns=None):
    """Generate a single realistic training record."""
    
    # Select project type with weights
    project_type_idx = np.random.choice(len(PROJECT_TYPE_WEIGHTS), p=PROJECT_TYPE_WEIGHTS)
    project_type_name = PROJECT_TYPES[project_type_idx]
    
    # Branch type and name
    branch_type_idx = np.random.choice(5, p=[0.35, 0.20, 0.25, 0.10, 0.10])
    branch_name = generate_branch_name(branch_type_idx)
    is_release = branch_type_idx == 4 or branch_type_idx == 2  # release branch or main
    
    # Environment
    env_idx = np.random.choice(3, p=[0.50, 0.30, 0.20])  # dev, staging, prod
    
    # Build type
    build_type = 1 if is_release or random.random() < 0.15 else 0  # 0=debug, 1=release
    
    # Repository size
    repo_size = random.uniform(*REPO_SIZES[project_type_name])
    
    # IS_MONOREPO - ~15% of large projects, more common for large repos
    is_monorepo = 0
    if repo_size > 1000:  # Large repos more likely to be monorepos
        is_monorepo = 1 if random.random() < 0.25 else 0
    elif repo_size > 500:
        is_monorepo = 1 if random.random() < 0.15 else 0
    else:
        is_monorepo = 1 if random.random() < 0.05 else 0
    
    # Generate pipeline configuration
    pipeline_config = generate_pipeline_config(project_type_name, is_release)
    
    # HAS_ARTIFACT_PUBLISH - tied to release builds and deploy stage
    has_artifact_publish = 0
    if build_type == 1:  # Release build
        has_artifact_publish = 1 if random.random() < 0.75 else 0
    elif pipeline_config['has_deploy_stage']:
        has_artifact_publish = 1 if random.random() < 0.40 else 0
    
    # Cache state
    is_first_build = 1 if random.random() < 0.08 else 0
    cache_available = 0 if is_first_build else (1 if random.random() < 0.85 else 0)
    
    # IS_CLEAN_BUILD - ~10% of builds, more common after failures or for release
    is_clean_build = 0
    if build_type == 1:  # Release builds more likely to be clean
        is_clean_build = 1 if random.random() < 0.25 else 0
    else:
        is_clean_build = 1 if random.random() < 0.08 else 0
    
    # Git metrics (pass has_tests to generate test_files_changed)
    is_small_change = random.random() < 0.35
    has_tests = pipeline_config['has_unit_tests'] or pipeline_config['has_integration_tests']
    git_metrics = generate_git_metrics(project_type_name, is_small_change, has_tests)
    
    # Calculate resources (now includes is_clean_build and is_monorepo impact)
    resources = calculate_resources(
        project_type_name, 
        pipeline_config, 
        cache_available, 
        is_first_build, 
        build_type == 1,
        is_clean_build,
        is_monorepo
    )
    
    # Status (use Kaggle rates if available)
    if kaggle_patterns:
        status_probs = kaggle_patterns['status_rates']
        status = np.random.choice(
            list(status_probs.keys()), 
            p=[status_probs.get(s, 0.25) for s in ['success', 'failed', 'running', 'skipped']]
        )
    else:
        status = np.random.choice(['success', 'failed'], p=[0.88, 0.12])
    
    # Build timestamp with realistic hour distribution
    # More builds during work hours (9-18), fewer at night
    hour_weights = [0.01, 0.01, 0.01, 0.01, 0.02, 0.02, 0.03, 0.05,  # 0-7
                    0.08, 0.10, 0.10, 0.10, 0.08, 0.10, 0.10, 0.08,  # 8-15
                    0.05, 0.03, 0.02, 0.01, 0.01, 0.01, 0.01, 0.01]  # 16-23
    # Normalize to ensure sum = 1
    hour_weights = [w / sum(hour_weights) for w in hour_weights]
    hour = np.random.choice(24, p=hour_weights)
    timestamp = base_timestamp + timedelta(
        hours=hour,
        minutes=random.randint(0, 59)
    )
    
    # TIME_OF_DAY_HOUR - extracted from timestamp
    time_of_day_hour = hour
    
    # Generate IDs
    build_id = f"build-{record_id:04d}"
    pipeline_id = f"pipe-{hashlib.md5(str(random.random()).encode()).hexdigest()[:5]}"
    commit_id = hashlib.sha1(str(random.random()).encode()).hexdigest()
    
    return {
        'build_id': build_id,
        'timestamp': timestamp.isoformat(),
        'pipeline_id': pipeline_id,
        'commit_id': commit_id,
        # Project context
        'project_type': project_type_idx,
        'project_type_name': project_type_name,
        'repo_size_mb': round(repo_size, 1),
        'is_monorepo': is_monorepo,  # NEW
        # Branch context
        'branch': branch_name,
        'branch_type': branch_type_idx,
        'build_type': build_type,
        'environment': env_idx,
        # Git metrics
        **git_metrics,
        # Pipeline config
        **pipeline_config,
        'has_artifact_publish': has_artifact_publish,  # NEW
        # Cache/Build state
        'is_first_build': is_first_build,
        'cache_available': cache_available,
        'is_clean_build': is_clean_build,  # NEW
        # Time context
        'time_of_day_hour': time_of_day_hour,  # NEW
        # Resources (TARGETS)
        **resources,
        # Status
        'status': status,
    }


def generate_dataset(num_records=1000, kaggle_path=None):
    """Generate the complete enhanced training dataset."""
    
    print(f"\n{'='*60}")
    print(f"Generating Enhanced ML Training Dataset")
    print(f"{'='*60}")
    
    # Load Kaggle patterns if available
    kaggle_df = load_kaggle_data(kaggle_path) if kaggle_path else None
    kaggle_patterns = extract_pipeline_patterns(kaggle_df)
    
    # Generate records
    records = []
    base_timestamp = datetime(2024, 1, 1)
    
    print(f"\n⏳ Generating {num_records} records...")
    
    for i in range(num_records):
        # Progress every 100 records
        base_date = base_timestamp + timedelta(days=i // 50)  # ~50 builds per day
        record = generate_single_record(i + 1, base_date, kaggle_patterns)
        records.append(record)
        
        if (i + 1) % 200 == 0:
            print(f"   Generated {i + 1}/{num_records} records...")
    
    # Create DataFrame
    df = pd.DataFrame(records)
    
    # Print statistics
    print(f"\n{'='*60}")
    print("Dataset Statistics")
    print(f"{'='*60}")
    print(f"\nTotal records: {len(df)}")
    print(f"\nProject Type Distribution:")
    print(df['project_type_name'].value_counts())
    print(f"\nBuild Type Distribution:")
    print(df['build_type'].map({0: 'debug', 1: 'release'}).value_counts())
    print(f"\nResource Ranges:")
    print(f"  Memory GB: {df['memory_gb'].min():.1f} - {df['memory_gb'].max():.1f}")
    print(f"  CPU %:     {df['cpu_avg_pct'].min():.1f} - {df['cpu_avg_pct'].max():.1f}")
    print(f"  Time min:  {df['build_time_min'].min():.1f} - {df['build_time_min'].max():.1f}")
    print(f"\nCache hit rate: {df['cache_available'].mean()*100:.1f}%")
    print(f"E2E test rate:  {df['has_e2e_tests'].mean()*100:.1f}%")
    print(f"Emulator rate:  {df['uses_emulator'].mean()*100:.1f}%")
    print(f"\n--- NEW FEATURES ---")
    print(f"Clean build rate:    {df['is_clean_build'].mean()*100:.1f}%")
    print(f"Monorepo rate:       {df['is_monorepo'].mean()*100:.1f}%")
    print(f"Artifact publish:    {df['has_artifact_publish'].mean()*100:.1f}%")
    print(f"Avg test files:      {df['test_files_changed'].mean():.1f}")
    print(f"Peak hours (9-17):   {(df['time_of_day_hour'].between(9, 17)).mean()*100:.1f}%")
    
    return df


def main():
    """Main entry point."""
    # Paths
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    
    kaggle_path = os.path.join(project_root, 'ci_cd_logs.csv')
    output_path = os.path.join(script_dir, 'enhanced_training_data.csv')
    
    # Generate dataset
    df = generate_dataset(num_records=1000, kaggle_path=kaggle_path)
    
    # Save to CSV
    df.to_csv(output_path, index=False)
    print(f"\n✅ Dataset saved to: {output_path}")
    print(f"   Columns: {len(df.columns)}")
    print(f"   Records: {len(df)}")
    
    # Also save a feature-only version for training (with NEW features)
    training_cols = [
        'project_type', 'repo_size_mb', 'is_monorepo',  # NEW: is_monorepo
        'branch_type', 'build_type', 'environment',
        'files_changed', 'lines_added', 'lines_deleted', 'source_files_pct',
        'deps_file_changed', 'dependency_count', 'test_files_changed',  # NEW: test_files_changed
        'stages_count', 'has_build_stage', 'has_unit_tests', 'has_integration_tests',
        'has_e2e_tests', 'has_deploy_stage', 'has_docker_build', 'uses_emulator',
        'parallel_stages', 'has_artifact_publish',  # NEW: has_artifact_publish
        'is_first_build', 'cache_available', 'is_clean_build',  # NEW: is_clean_build
        'time_of_day_hour',  # NEW: time_of_day_hour
        'cpu_avg_pct', 'memory_gb', 'build_time_min'  # Targets
    ]
    
    df_training = df[training_cols]
    training_output = os.path.join(script_dir, 'training_features.csv')
    df_training.to_csv(training_output, index=False)
    print(f"\n✅ Training features saved to: {training_output}")
    print(f"   Columns: {len(df_training.columns)} (including 5 NEW features)")
    
    return df


if __name__ == "__main__":
    main()
