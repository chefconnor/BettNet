# Sports Betting Analytics Project - Task Tracker

## Open Questions & Tasks

### ML Algorithm Selection
- [ ] Identify suitable real-time ML algorithms for odds analysis
- [ ] Research ensemble methods that work well with streaming data
- [ ] Define feature engineering approach for basketball and football data
- [ ] Determine evaluation metrics for model performance
- [ ] Research algorithms specifically for detecting delayed line movements
- [ ] Explore time series anomaly detection techniques applicable to odds movement

### Training Data Window Management
- [ ] Define optimal historical window size (initial parameters: min 30 days, max 365 days)
- [ ] Research decay functions for weighing recent vs. older data
- [ ] Establish retraining frequency based on data volume
- [ ] Plan adaptive window sizing based on basketball and football seasons
- [ ] Consider separate window strategies for different sports (NBA vs NFL scheduling differences)

### Sports & Odds APIs
- [ ] Research available basketball and football data APIs (candidates: The Odds API, SportsData.io)
- [ ] Compare pricing, rate limits, and data coverage
- [ ] Evaluate data freshness and update frequency
- [ ] Select primary and fallback data sources
- [ ] Investigate NBA-specific data sources
- [ ] Investigate NFL-specific data sources
- [ ] Assess historical data availability for backtesting models

### Arbitrage Detection
- [ ] Design algorithm to identify arbitrage opportunities across bookmakers
- [ ] Implement real-time cross-bookmaker odds comparison
- [ ] Create risk assessment for arbitrage opportunities (accounting for timing risk)
- [ ] Develop notification system for viable arbitrage opportunities
- [ ] Research bookmaker-specific rules that might affect arbitrage execution
- [ ] Create metrics to track theoretical arbitrage profit over time

### Betting DSL with SpEL
- [ ] Define core DSL syntax for betting rules
- [ ] Create expression templates for common betting patterns
- [ ] Design validation rules for betting expressions
- [ ] Establish risk management primitives within DSL
- [ ] Create sport-specific functions and templates
- [ ] Add arbitrage-specific operators and functions to the DSL

### Data Storage Strategy
- [ ] Select database for historical odds storage
- [ ] Design schema for efficient querying of time-series odds data
- [ ] Plan data retention policy
- [ ] Implement efficient indexing for real-time queries
- [ ] Create separate storage plans for historical vs. live game data
- [ ] Design storage for cross-bookmaker comparison

### API Streaming Simulation
- [ ] Design Spring Integration pipeline for non-streaming API
- [ ] Create timestamp-based replay mechanism
- [ ] Implement variable speed replay (real-time, accelerated)
- [ ] Add fault simulation capability
- [ ] Develop method for simulating realistic odds movement patterns

## Completed Tasks
- [x] Determine initial sports focus (basketball, American football)
- [x] Identify primary anomaly detection targets (delayed lines, odds not reflecting model)
- [x] Add arbitrage detection to project scope

## Decisions Made
- Focus on basketball and American football initially
- Target detecting delayed line movements and odds that don't align with ML predictions
- Include arbitrage opportunity detection across multiple bookmakers
- Consider The Odds API as primary data source due to multiple bookmaker coverage
