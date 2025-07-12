# Training Data Window Management for Sports Betting ML

## Time Decay Approaches for Historical Data

When building ML models for sports betting, the age of data matters. Games from 5 years ago contain valuable patterns, but their relevance diminishes over time due to:

- Changes in team rosters and coaching staff
- Evolution of playing styles and strategies
- Rule changes affecting gameplay
- Changes in league competitiveness

However, completely discarding older data would waste valuable information. The solution is **weighted training windows** that balance recency with sufficient sample size.

## Recommended Decay Functions for Sports Data

### 1. Exponential Decay

The most common and effective approach for sports data:

```
weight = exp(-λ * age_in_days)
```

Where:
- `λ` (lambda) is the decay factor (typically 0.001-0.005 for sports)
- `age_in_days` is how old the data point is

**Advantages:**
- Gradually reduces importance of older data without hard cutoffs
- Tunable decay parameter to adjust how quickly data becomes less relevant
- Well-understood mathematical properties

**Implementation Example:**

```java
public double calculateExponentialWeight(LocalDate gameDate, LocalDate currentDate) {
    // Decay factor: smaller means slower decay
    double lambda = 0.003;
    
    long daysBetween = ChronoUnit.DAYS.between(gameDate, currentDate);
    return Math.exp(-lambda * daysBetween);
}
```

### 2. Season-Based Step Function

Sports naturally follow seasonal patterns, so a step function can work well:

```
weight = baseWeight * seasonMultiplier
```

Where:
- Current season: 1.0
- Last season: 0.7
- Two seasons ago: 0.4
- Three seasons ago: 0.2
- Four+ seasons ago: 0.1

**Advantages:**
- Aligns with natural breaks in sports (offseason changes)
- Easy to explain and implement
- Can be adjusted for significant known changes (e.g., major trades)

### 3. Hybrid: Exponential with Season Reset

Combines continuous decay with season boundaries:

```
weight = exp(-λ * days_since_season_start) * seasonMultiplier
```

This resets the day counter at each season but still applies a season discount.

## Specific Considerations for Basketball and Football

### NBA Basketball

- **Roster Turnover:** Generally high with player movement and trades
- **Recommended λ:** 0.004-0.005 (higher decay rate)
- **Useful Historical Window:** 2-3 seasons, with rapidly diminishing weight beyond that
- **Key Temporal Factors:** 
  - Trade deadlines cause mid-season shifts
  - Coach changes can dramatically impact team performance
  - Back-to-back games significantly affect performance

### NFL Football

- **Roster Turnover:** Moderate overall, but quarterback changes have outsized impact
- **Recommended λ:** 0.002-0.003 (moderate decay rate)
- **Useful Historical Window:** 3-5 seasons, with QB-consistency as a modifier
- **Key Temporal Factors:**
  - Short 17-game season means less in-season data
  - Weekly game format means more rest between games
  - Injuries have major impact due to smaller roster sizes

## Implementation Strategy

### 1. Data Storage Approach

- Store all historical data without pre-filtering
- Apply weights during model training phase
- Re-evaluate weights periodically without needing to recollect data

### 2. Dynamic Weight Calculation

```java
public class DataWeightingService {
    
    // Configuration parameters
    private double baseLambda = 0.003;  // Base decay factor
    private Map<String, Double> sportSpecificLambdas;  // Sport-specific adjustments
    
    public DataWeightingService() {
        sportSpecificLambdas = new HashMap<>();
        sportSpecificLambdas.put("NBA", 0.005);
        sportSpecificLambdas.put("NFL", 0.0025);
    }
    
    /**
     * Calculate weight for a historical game data point
     */
    public double calculateWeight(SportEvent event, LocalDate currentDate) {
        String sport = event.getSport();
        LocalDate gameDate = event.getStartTime().toLocalDate();
        
        // Get sport-specific lambda or use default
        double lambda = sportSpecificLambdas.getOrDefault(sport, baseLambda);
        
        // Calculate days between
        long daysDifference = ChronoUnit.DAYS.between(gameDate, currentDate);
        
        // Apply exponential decay
        return Math.exp(-lambda * daysDifference);
    }
    
    /**
     * Apply season-based multiplier adjustments
     */
    public double applySeasonalAdjustment(double weight, int seasonsDifference) {
        if (seasonsDifference == 0) return weight;          // Current season
        else if (seasonsDifference == 1) return weight * 0.7;  // Last season
        else if (seasonsDifference == 2) return weight * 0.4;  // Two seasons ago
        else if (seasonsDifference == 3) return weight * 0.2;  // Three seasons ago
        else return weight * 0.1;                           // Four+ seasons ago
    }
}
```

### 3. Integration with ML Pipeline

```java
public class ModelTrainingPipeline {
    
    private final DataWeightingService weightingService;
    
    // During model training:
    public Dataset<Row> applyWeightsToTrainingData(Dataset<Row> data) {
        // Create UDF for weight calculation
        sparkSession.udf().register("calculateWeight", 
            (Column gameDate, Column sport) -> {
                // Call weighting service
                return weightingService.calculateWeight(
                    sport.toString(), 
                    LocalDate.parse(gameDate.toString()), 
                    LocalDate.now());
            }, DataTypes.DoubleType);
        
        // Apply weights
        return data.withColumn("sample_weight", 
            callUDF("calculateWeight", col("game_date"), col("sport")));
    }
    
    // Use weights during model training
    public void trainModelsWithWeightedData(Dataset<Row> weightedData) {
        // Different ML frameworks handle sample weights differently
        
        // For Spark ML:
        LogisticRegression lr = new LogisticRegression()
            .setWeightCol("sample_weight");
            
        // For DL4J through custom loss function, etc.
    }
}
```

## Analysis: Value of 5-Year-Old Data

For NBA and NFL specifically:

### NBA (5 years ago vs. today)
- **Player Roster:** <10% of players typically remain on same team after 5 years
- **Coaching Staff:** Average NBA coach tenure is ~3.8 years
- **Playing Style:** NBA has dramatically shifted to 3-point emphasis in recent years
- **Estimated Information Value:** ~5-15% compared to recent data

### NFL (5 years ago vs. today)
- **Player Roster:** ~15-20% of non-QB players remain after 5 years; QB consistency is key
- **Coaching Staff:** Average NFL coach tenure is ~4.3 years
- **System Changes:** Offensive/defensive schemes often remain similar with same coach
- **Estimated Information Value:** ~10-20% compared to recent data, up to 30% if QB and coach remain the same

## Recommendation

1. **Use a 5-year sliding window** that applies exponential decay
2. **Adjust decay parameters by sport** (faster decay for NBA than NFL)
3. **Apply additional multipliers for major team changes** (coaching changes, star player trades)
4. **Monitor model performance when excluding older data** to fine-tune your decay parameters
5. **Consider separate models** for different time horizons (e.g., one using only current season, one using 3 years of data)

This approach gives you the flexibility to incorporate long-term patterns while emphasizing recent performance, optimizing your ML models for sports betting prediction.
