# Arbitrage Detection in Sports Betting

## What is Arbitrage Betting?

Arbitrage betting (often called "arbing") is a technique where a bettor places wagers on all possible outcomes of an event at odds that guarantee a profit regardless of the result. This is possible when different bookmakers offer different odds on the same event, creating a mathematical opportunity.

## How Arbitrage Works

### Example: Two-Way Market (Basketball Point Spread)

Consider a basketball game between Team A and Team B:

- Bookmaker 1: Team A (-5.5) @ +100 (2.0 decimal odds)
- Bookmaker 2: Team B (+5.5) @ +120 (2.2 decimal odds)

**Mathematical check:**
1. Calculate implied probability: 1/2.0 = 0.50 (50%) and 1/2.2 = 0.45 (45%)
2. Total implied probability: 0.50 + 0.45 = 0.95 (95%)
3. Since total < 100%, an arbitrage opportunity exists

**Bet calculation:**
- With $1000 total stake:
   - Stake on Team A = $1000 × (0.50/0.95) = $526.32
   - Stake on Team B = $1000 × (0.45/0.95) = $473.68
   
**Profit scenario:**
- If Team A covers: $526.32 × 2.0 = $1052.64 (profit of $52.64)
- If Team B covers: $473.68 × 2.2 = $1042.10 (profit of $42.10)

Guaranteed profit regardless of outcome.

## Implementation in Our System

### 1. Data Collection
- Collect odds from multiple bookmakers via The Odds API
- Normalize all odds to decimal format for easier comparison
- Store odds with timestamps for historical analysis

### 2. Real-time Detection Algorithm
```java
public List<ArbitrageOpportunity> findArbitrageOpportunities(SportEvent event) {
    Map<String, List<OddsData>> bookmakerOdds = getLatestOddsByBookmaker(event.getId());
    List<ArbitrageOpportunity> opportunities = new ArrayList<>();
    
    // For each market type (spread, moneyline, etc.)
    for (String marketType : getAvailableMarkets(bookmakerOdds)) {
        // Find combinations of bookmakers that create arbitrage
        opportunities.addAll(calculateArbitrageForMarket(marketType, bookmakerOdds));
    }
    
    return opportunities;
}
```

### 3. Mathematical Components
- Calculate implied probability from decimal odds: `impliedProbability = 1/decimalOdds`
- Determine arbitrage existence: `sumOfImpliedProbabilities < 1.0`
- Calculate optimal stake distribution: `stakeForOutcome = totalStake * (impliedProbabilityForOutcome / sumOfImpliedProbabilities)`
- Determine expected profit: `expectedProfit = (1 - sumOfImpliedProbabilities) / sumOfImpliedProbabilities * totalStake`

### 4. Risk Assessment
- Time delay risk: Odds might change before all bets are placed
- Calculation errors: Verify all calculations with multiple methods
- Bookmaker restrictions: Some may limit or ban accounts used for arbitrage
- Terms and conditions: Different rules for void bets could affect arbitrage

### 5. Integration with ML Models
- Use ML to predict which arbitrage opportunities are most likely to remain valid
- Prioritize opportunities based on expected profit and risk factors
- Track arbitrage availability patterns to predict future opportunities

### 6. SpEL DSL For Arbitrage
Example expressions for arbitrage in our DSL:
```
// Basic arbitrage detection
arbitrage.exists(event) AND arbitrage.profit > 0.02  // 2% profit minimum

// More complex rule
arbitrage.exists(event) AND 
arbitrage.profit > 0.015 AND 
arbitrage.bookmakers.contains("DraftKings", "FanDuel") AND
arbitrage.timeToEventStart > 30MINUTES
```

### 7. Performance Metrics to Track
- Number of arbitrage opportunities identified
- Average profit margin per opportunity
- Success rate (opportunities that remained valid until execution)
- Total theoretical profit over time
- Distribution of opportunities by sport/market type

### 8. Implementation Considerations
- Processing must be real-time to catch short-lived opportunities
- Database schema must support efficient cross-bookmaker queries
- Consider separating arbitrage detection from ML prediction pipeline for lower latency
- Implement notification system for high-value opportunities
