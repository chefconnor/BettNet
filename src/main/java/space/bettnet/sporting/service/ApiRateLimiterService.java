package space.bettnet.sporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import space.bettnet.sporting.model.ratelimit.ApiConfig;
import space.bettnet.sporting.model.ratelimit.RateLimit;
import space.bettnet.sporting.repository.ApiConfigRepository;

import javax.annotation.PostConstruct;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing API rate limits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiRateLimiterService {

    private final ApiConfigRepository apiConfigRepository;
    private final Map<String, ApiConfig> apiConfigCache = new ConcurrentHashMap<>();

    /**
     * Initializes the service by loading all API configurations from storage.
     */
    @PostConstruct
    public void init() {
        loadAllConfigs();
    }

    /**
     * Checks if a request to the specified API is allowed based on rate limits.
     * If allowed, it records the usage.
     *
     * @param baseUrl The base URL of the API
     * @return true if the request is allowed, false otherwise
     */
    public boolean allowRequest(String baseUrl) {
        ApiConfig config = getOrCreateApiConfig(baseUrl);

        // Check all rate limits for this API
        for (RateLimit limit : config.getRateLimits()) {
            if (!isWithinRateLimit(limit)) {
                log.warn("Rate limit exceeded for {} (limit: {} per {})",
                    baseUrl, limit.getMaxCount(), limit.getTimeUnit());
                return false;
            }
        }

        // If all limits are satisfied, record the usage
        recordUsage(config);
        return true;
    }

    /**
     * Records API usage for all rate limits of the given API config.
     *
     * @param config The API configuration
     */
    private void recordUsage(ApiConfig config) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime record = ZonedDateTime.now();

        for (RateLimit limit : config.getRateLimits()) {
            limit.getUsageTimestamps().add(record);
        }

        // For short-term limits (seconds, minutes), update immediately
        // For longer-term limits, we'll sync during the scheduled task
        if (hasShortTermLimits(config)) {
            apiConfigRepository.save(config);
        }

        // Update the cache
        apiConfigCache.put(config.getBaseUrl(), config);
    }

    /**
     * Checks if the API has any short-term rate limits (seconds or minutes).
     */
    private boolean hasShortTermLimits(ApiConfig config) {
        return config.getRateLimits().stream()
            .anyMatch(limit -> limit.getTimeUnit() == ChronoUnit.SECONDS ||
                               limit.getTimeUnit() == ChronoUnit.MINUTES);
    }

    /**
     * Checks if the current usage is within the specified rate limit.
     *
     * @param limit The rate limit to check
     * @return true if within limit, false otherwise
     */
    private boolean isWithinRateLimit(RateLimit limit) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime cutoffTime = calculateCutoffTime(now, limit.getTimeUnit());

        // Count usage records after the cutoff time
        long usageCount = limit.getUsageTimestamps().stream()
            .filter(record -> record.isAfter(cutoffTime))
            .count();

        return usageCount < limit.getMaxCount();
    }

    /**
     * Calculates the cutoff time for the specified time unit.
     */
    private ZonedDateTime calculateCutoffTime(ZonedDateTime now, ChronoUnit timeUnit) {
        return now.minus(1, timeUnit);
    }

    /**
     * Gets an API config from the cache or creates a new one if not found.
     */
    private ApiConfig getOrCreateApiConfig(String baseUrl) {
        return apiConfigCache.computeIfAbsent(baseUrl, url -> {
            Optional<ApiConfig> config = apiConfigRepository.findByBaseUrl(url);
            if (config.isPresent()) {
                return config.get();
            } else {
                ApiConfig newConfig = new ApiConfig();
                newConfig.setId(url);
                newConfig.setBaseUrl(url);
                newConfig.setLastUpdated(ZonedDateTime.now());
                return newConfig;
            }
        });
    }

    /**
     * Loads all API configurations from storage into the cache.
     */
    private void loadAllConfigs() {
        List<ApiConfig> configs = apiConfigRepository.findAll();
        configs.forEach(config -> apiConfigCache.put(config.getBaseUrl(), config));
        log.info("Loaded {} API configurations from storage", configs.size());
    }

    /**
     * Scheduled task to sync the rate limit data with storage.
     * Runs every 10 minutes.
     */
    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    public void syncRateLimitsWithStorage() {
        log.debug("Starting scheduled sync of rate limits with storage");

        apiConfigCache.values().forEach(this::cleanupExpiredRecords);

        // Save all configs to storage
        apiConfigCache.values().forEach(apiConfigRepository::save);

        // Reload all configs to get any changes made elsewhere
        loadAllConfigs();

        log.debug("Completed scheduled sync of rate limits with storage");
    }

    /**
     * Cleans up expired usage records for all rate limits in an API config.
     */
    private void cleanupExpiredRecords(ApiConfig config) {
        ZonedDateTime now = ZonedDateTime.now();

        for (RateLimit limit : config.getRateLimits()) {
            ZonedDateTime cutoffTime = calculateCutoffTime(now, limit.getTimeUnit());

            List<ZonedDateTime> validRecords = limit.getUsageTimestamps().stream()
                .filter(record -> record.isAfter(cutoffTime))
                .collect(Collectors.toList());

            if (validRecords.size() < limit.getUsageTimestamps().size()) {
                log.debug("Cleaned up {} expired records for {} ({})",
                    limit.getUsageTimestamps().size() - validRecords.size(),
                    config.getBaseUrl(), limit.getId());
                limit.setUsageTimestamps(validRecords);
            }
        }
    }

    /**
     * Creates or updates an API configuration with rate limits.
     *
     * @param config The API configuration to update
     * @return The updated API configuration
     */
    public ApiConfig updateApiConfig(ApiConfig config) {
        ApiConfig saved = apiConfigRepository.save(config);
        apiConfigCache.put(config.getBaseUrl(), saved);
        return saved;
    }
}
