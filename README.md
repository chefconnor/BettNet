# Sporting Analytics Platform

A real-time sports betting analytics system that leverages ML algorithms to identify betting opportunities through odds analysis, pattern recognition, and arbitrage detection.

## Project Overview

This portfolio project demonstrates real-time data engineering skills by:
- Ingesting sports and odds data from multiple APIs
- Processing streams of data through ML algorithms
- Identifying betting opportunities based on market inefficiencies
- Managing paper trades through a rule-based system
- Evaluating performance and implementing self-correction

## Documentation Index

All planning and design documents can be found in the `/docs` folder:

| Document | Description |
|----------|-------------|
| [SPORTS_API_OPTIONS.md](/docs/SPORTS_API_OPTIONS.md) | Analysis of available basketball and football data APIs |
| [ML_ALGORITHM_ANALYSIS.md](/docs/ML_ALGORITHM_ANALYSIS.md) | Evaluation of ML algorithms (autoencoders, tree-based, RNNs) for odds analysis |
| [ARBITRAGE_DETECTION.md](/docs/ARBITRAGE_DETECTION.md) | Implementation strategy for cross-bookmaker arbitrage detection |
| [TRAINING_WINDOWS.md](/docs/TRAINING_WINDOWS.md) | Approaches for managing training data windows with time decay functions |

## Project Planning

The following files track our planning process and decisions:

| File | Purpose |
|------|---------|
| [PROJECT_CHECKPOINT.md](/PROJECT_CHECKPOINT.md) | Records key decisions, pending items, and overall project status |
| [TASK_TRACKER.md](/TASK_TRACKER.md) | Detailed list of tasks organized by project component |

## Current Decisions

- **Sports Focus**: NBA Basketball and NFL Football
- **Detection Targets**: 
  - Delayed line movements
  - Odds that don't reflect ML model predictions
  - Arbitrage opportunities across bookmakers
- **ML Approach**: Ensemble system combining autoencoders, tree-based algorithms, and RNNs
- **Data Source**: Considering The Odds API for comprehensive bookmaker coverage
- **Training Window**: 5-year sliding window with exponential decay weighting

## Next Steps

Refer to [TASK_TRACKER.md](/TASK_TRACKER.md) for the current list of open tasks and progress. Key focus areas include:
- Finalizing API selection
- Completing ML algorithm specifications
- Designing betting DSL with SpEL
- Creating data storage strategy
- Implementing API streaming simulation

## Technical Stack

- Java 21
- Spring Boot
- Apache Kafka for real-time streaming
- Apache Spark for ML processing
- Apache Airflow for workflow orchestration
- H2 Database (development)
