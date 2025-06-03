package space.bettnet.sporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import space.bettnet.sporting.model.ratelimit.ApiConfig;
import space.bettnet.sporting.model.ratelimit.RateLimit;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Service that wraps API calls with rate limiting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiClientWrapper {

    private final ApiRateLimiterService rateLimiterService;

    /**
     * Executes an API call with rate limiting checks.
     *
     * @param baseUrl The base URL of the API
     * @param apiCall The API call to execute if rate limit allows
     * @param <T> The return type of the API call
     * @return The result of the API call, or empty if rate limited
     */
    public <T> Optional<T> executeWithRateLimit(String baseUrl, Supplier<T> apiCall) {
        // Check if the request is allowed by rate limits
        if (!rateLimiterService.allowRequest(baseUrl)) {
            log.warn("API call to {} was rate limited", baseUrl);
            return Optional.empty();
        }

        try {
            T result = apiCall.get();
            return Optional.ofNullable(result);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.warn("Received 429 Too Many Requests from {}", baseUrl);
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Error executing API call to {}: {}", baseUrl, ex.getMessage());
            throw ex;
        }
    }


    /**
     * Configures rate limits for an API.
     *
     * @param baseUrl The base URL of the API
     * @param apiKey The API key (optional)
     * @param perMinuteLimit The number of requests allowed per minute
     * @param perDayLimit The number of requests allowed per day
     * @return The updated API configuration
     */
    public ApiConfig configureRateLimits(String baseUrl, String apiKey, int perMinuteLimit, int perDayLimit) {
        ApiConfig config = new ApiConfig();
        config.setId(baseUrl);
        config.setBaseUrl(baseUrl);
        config.setApiKey(apiKey);

        // Configure per-minute rate limit
        RateLimit perMinute = new RateLimit();
        perMinute.setId("per-minute");
        perMinute.setMaxCount(perMinuteLimit);
        perMinute.setTimeUnit(ChronoUnit.MINUTES);
        config.getRateLimits().add(perMinute);

        // Configure per-day rate limit
        RateLimit perDay = new RateLimit();
        perDay.setId("per-day");
        perDay.setMaxCount(perDayLimit);
        perDay.setTimeUnit(ChronoUnit.DAYS);
        config.getRateLimits().add(perDay);

        return rateLimiterService.updateApiConfig(config);
    }
}
