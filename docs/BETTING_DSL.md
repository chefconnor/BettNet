# Betting DSL Design Using Spring Expression Language (SpEL)

## Overview

The Betting Domain-Specific Language (DSL) using Spring Expression Language (SpEL) provides a flexible and powerful way to define betting strategies and rules. This allows for complex condition evaluation without hardcoding rules in Java, enabling dynamic strategy adjustments.

## DSL Requirements

Our betting DSL should support:

1. **Condition evaluation** for deciding when to place bets
2. **Risk management** to set stake limits and exposure constraints
3. **Multi-factor analysis** combining different signals from ML models
4. **Arbitrage specifications** for cross-bookmaker betting
5. **Sport-specific rule expressions** accommodating differences between basketball and football

## SpEL Fundamentals for Our DSL

Spring Expression Language offers several features that make it ideal for our betting DSL:

- **Expression evaluation**: `parser.parseExpression("expression").getValue(context)`
- **Property access**: `game.homeTeam`, `odds.moneyline.home`
- **Method invocation**: `models.getPrediction(game)`
- **Operators**: Comparison (`>`, `<`, `==`), logical (`and`, `or`, `not`)
- **Collection access**: `bookmakers[0].odds`
- **Elvis operator**: `game.score ?: 0` (null-safe access)

## DSL Design

### 1. Core Expression Structure

```
[CONDITION] AND [RISK_CHECK] => [ACTION]
```

Where:
- `CONDITION` determines if a betting opportunity exists
- `RISK_CHECK` verifies the bet meets risk management criteria
- `ACTION` defines what bet to place (type, stake, etc.)

### 2. Context Root Objects

The DSL will expose the following root objects in the evaluation context:

- `event` - The sporting event (teams, start time, etc.)
- `odds` - Odds data from different bookmakers
- `models` - ML model predictions and confidence scores
- `account` - Account status including bankroll and existing bets
- `history` - Historical performance data for teams/markets
- `arbitrage` - Arbitrage calculation helper

### 3. Sample Expressions

#### Basic Value Betting
```
models.predictedOdds('HOME_WIN') < odds.best('HOME_WIN') && 
models.confidence > 0.65 && 
account.exposure(event) < account.maxExposurePerEvent
```

#### Delayed Line Movement Detection
```
odds.movement('HOME_WIN', '1H') > 0.2 && 
models.predicts('HOME_WIN') && 
odds.divergence(models.predictedOdds('HOME_WIN')) > 0.1
```

#### Arbitrage Opportunity
```
arbitrage.exists(event) && 
arbitrage.profit > 0.03 && 
arbitrage.maxStake > 100 && 
!bookmakers.restricted().containsAny(arbitrage.bookmakers())
```

#### Basketball-Specific Rule
```
event.sport == 'NBA' && 
history.teamRest('HOME') > 2 && 
history.teamRest('AWAY') == 0 && 
models.predictedTotal() > odds.total() + 5
```

#### Football-Specific Rule with Time Window
```
event.sport == 'NFL' && 
event.quarter == 4 && 
event.timeRemaining < 180 && 
event.scoreDifference < 7 && 
odds.liveSpread('HOME') > 3.5
```

## Implementation Strategy

### 1. Core SpEL Context Classes

```java
@Component
public class BettingDslEvaluator {
    private final ExpressionParser parser = new SpelExpressionParser();
    
    public boolean evaluateCondition(String expression, EvaluationContext context) {
        Expression exp = parser.parseExpression(expression);
        return exp.getValue(context, Boolean.class);
    }
    
    public BettingAction evaluateAction(String expression, EvaluationContext context) {
        Expression exp = parser.parseExpression(expression);
        return exp.getValue(context, BettingAction.class);
    }
}

@Component
public class BettingContextBuilder {
    // Dependencies
    private final OddsService oddsService;
    private final ModelPredictionService modelService;
    private final AccountService accountService;
    private final HistoricalDataService historyService;
    private final ArbitrageService arbitrageService;
    
    public EvaluationContext buildContext(SportEvent event) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // Root objects
        context.setVariable("event", event);
        context.setVariable("odds", oddsService.getOddsForEvent(event.getId()));
        context.setVariable("models", modelService.getPredictionsForEvent(event.getId()));
        context.setVariable("account", accountService.getAccountStatus());
        context.setVariable("history", historyService.getHistoricalContext(event));
        context.setVariable("arbitrage", arbitrageService.analyzeEvent(event.getId()));
        
        // Register functions
        context.registerFunction("percentile", 
            BettingFunctions.class.getDeclaredMethod("percentile", Double.class, Double[].class));
        
        return context;
    }
}
```

### 2. Rule Management System

The DSL expressions will be stored in a database and managed through a rule system:

```java
@Entity
public class BettingRule {
    @Id
    @GeneratedValue
    private Long id;
    
    private String name;
    private String description;
    private String conditionExpression;
    private String actionExpression;
    private boolean enabled;
    private int priority;
    
    @ElementCollection
    private List<String> appliedSports;
    
    // Other metadata
    private LocalDateTime createdAt;
    private LocalDateTime lastModified;
    private String createdBy;
}
```

### 3. Rule Execution Process

```java
@Service
public class BettingRuleExecutor {
    private final BettingRuleRepository ruleRepository;
    private final BettingDslEvaluator evaluator;
    private final BettingContextBuilder contextBuilder;
    private final BettingActionExecutor actionExecutor;
    
    public List<BettingAction> evaluateEventForBets(SportEvent event) {
        List<BettingAction> actions = new ArrayList<>();
        EvaluationContext context = contextBuilder.buildContext(event);
        
        // Get applicable rules
        List<BettingRule> rules = ruleRepository.findByEnabledTrueAndAppliedSportsContaining(event.getSport());
        
        // Sort by priority
        rules.sort(Comparator.comparing(BettingRule::getPriority).reversed());
        
        // Evaluate each rule
        for (BettingRule rule : rules) {
            if (evaluator.evaluateCondition(rule.getConditionExpression(), context)) {
                BettingAction action = evaluator.evaluateAction(rule.getActionExpression(), context);
                actions.add(action);
            }
        }
        
        return actions;
    }
}
```

## Custom Functions

To enhance the DSL, we'll implement custom functions:

```java
public class BettingFunctions {
    // Statistical functions
    public static boolean percentile(Double value, Double[] distribution, int percentile) {
        Arrays.sort(distribution);
        int index = (int) Math.ceil(distribution.length * percentile / 100.0) - 1;
        return value > distribution[index];
    }
    
    // Time-based functions
    public static boolean within(LocalDateTime time, int minutes, String direction) {
        LocalDateTime now = LocalDateTime.now();
        if ("before".equals(direction)) {
            return now.isBefore(time) && now.plusMinutes(minutes).isAfter(time);
        } else if ("after".equals(direction)) {
            return now.isAfter(time) && now.minusMinutes(minutes).isBefore(time);
        }
        return false;
    }
    
    // Sport-specific helper functions
    public static boolean backToBack(String teamId) {
        // Implementation to check if a team is playing in a back-to-back situation
        return false; // placeholder
    }
}
```

## UI Management

The DSL will be managed through a simple web interface that allows:

1. Creating new rules with a rule builder
2. Testing rules against historical data
3. Monitoring rule performance
4. Enabling/disabling rules
5. Setting rule priority

## Validation and Security

1. **Syntax validation** before saving rules
2. **Sandbox evaluation** to catch runtime errors
3. **Performance monitoring** to identify slow expressions
4. **Default timeouts** to prevent long-running expressions

## Example DSL Expressions Library

```
// Value betting based on model edge
models.predictedOdds('HOME_WIN') < odds.best('HOME_WIN') * 0.9 &&
models.confidence > 0.7 &&
account.riskForEvent(event) < account.maxRiskPerEvent

// Line movement momentum trading
odds.movement('SPREAD_HOME', '30M') < -1.5 && 
odds.volume('SPREAD_HOME') > odds.averageVolume('SPREAD_HOME') * 2 &&
!event.hasNewsIn(60)

// Arbitrage with risk management
arbitrage.exists() && 
arbitrage.profit > 0.025 && 
arbitrage.allBookmakersAccessible() &&
arbitrage.timeToPlace() < 30 &&
account.canPlaceBets(arbitrage.bookmakers())

// Weather-influenced totals bet (NFL)
event.sport == 'NFL' &&
event.weather.windSpeed > 20 &&
event.weather.precipitation > 30 &&
odds.total() > 45 &&
history.averageTotalInSimilarWeather() < odds.total() - 5
```

By implementing this DSL using SpEL, your system will have a flexible and powerful way to express betting strategies that can be modified without code changes, providing a key advantage for your real-time sports betting analytics platform.
