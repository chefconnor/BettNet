# Project Checkpoint - Sports Betting ML System

## Conversation History
- Initial project scope discussed on: May 25, 2025
- Focus: Real-time ML for sports betting odds analysis

## Current Project Status
- Planning phase
- Architecture and requirements definition
- Technology selection in progress

## Key Decisions Made
- Sports focus: Basketball and American football
- Anomaly detection targets: 
  - Delayed line movements
  - Odds that don't reflect ML model predictions
  - Arbitrage opportunities across multiple bookmakers
  - (Future consideration: Other market inefficiencies)

## Key Decisions Pending
- ML algorithms for odds analysis
- Training data window management
- Sports and odds APIs selection
- DSL for betting orders using SpEL
- Data storage strategy for API replay/simulation
- Approach for converting non-streaming API to simulated real-time

## Action Items
- Define ML algorithm requirements
- Research appropriate sports/odds APIs for basketball and football
- Design data windowing strategy
- Outline betting DSL structure
- Implement arbitrage detection algorithms

## Notes
- Project is a portfolio demonstration of real-time data engineering skills
- Focus on ensemble ML systems that identify mispriced/incorrect odds and anomalous states
- Need to simulate streaming for non-streaming APIs using Spring Integration
- Limited domain knowledge in sports betting - building system with technical focus
- The Odds API provides data from multiple bookmakers enabling arbitrage detection
