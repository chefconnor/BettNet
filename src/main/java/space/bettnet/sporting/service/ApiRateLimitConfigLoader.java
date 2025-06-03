package space.bettnet.sporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import space.bettnet.sporting.config.ApiRateLimitConfig;
import space.bettnet.sporting.model.ratelimit.ApiConfig;
import space.bettnet.sporting.model.ratelimit.RateLimit;
import space.bettnet.sporting.service.secrets.SecretManagerService;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for loading API rate limit configurations from YAML and GCS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiRateLimitConfigLoader {

    private final ApiRateLimitConfig yamlConfig;
    private final ApiRateLimiterService rateLimiterService;
    private final Storage storage;
    private final ResourceLoader resourceLoader;
    private final Environment environment;
    private final SecretManagerService secretManagerService;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Value("${api.config-bucket:sporting-configs}")
    private String gcpConfigBucket;

    @Value("${api.config-path:api-rate-limits}")
    private String gcpConfigPath;

    @Value("${api.local-config:classpath:rate-limits.yml}")
    private String localConfigPath;

    @Value("${api.rate-limit.auto-load:true}")
    private boolean autoLoadConfig;

    /**
     * Initialize the service by loading configurations from YAML if auto-load is enabled.
     */
    @PostConstruct
    public void init() {
        if (autoLoadConfig) {
            loadConfigs();
        }
    }

    /**
     * Loads API configurations from the appropriate source.
     * Uses GCS in cloud environments, local file otherwise.
     */
    public void loadConfigs() {
        if (isCloudEnvironment() && gcpConfigBucket != null) {
            loadConfigsFromGcs();
        } else {
            loadConfigsFromLocalYaml();
        }
    }

    /**
     * Checks if we're running in a cloud environment.
     */
    private boolean isCloudEnvironment() {
        return environment.acceptsProfiles(Profiles.of("cloud", "prod", "production"));
    }

    /**
     * Loads API configurations from GCS.
     */
    public void loadConfigsFromGcs() {
        try {
            log.info("Loading API configurations from GCS bucket: {}, path: {}", gcpConfigBucket, gcpConfigPath);

            String configFilePath = gcpConfigPath + "/rate-limits.yml";
            BlobId blobId = BlobId.of(gcpConfigBucket, configFilePath);
            Blob blob = storage.get(blobId);

            if (blob == null || !blob.exists()) {
                log.warn("No config file found in GCS at {}/{}", gcpConfigBucket, configFilePath);
                return;
            }

            String yamlContent = new String(blob.getContent(), StandardCharsets.UTF_8);
            ApiRateLimitConfig config = yamlMapper.readValue(yamlContent, ApiRateLimitConfig.class);

            // Process configs from YAML
            processApiEntries(config.getApis());
            log.info("Loaded {} API configurations from GCS", config.getApis().size());
        } catch (IOException e) {
            log.error("Failed to load API configurations from GCS", e);
        }
    }

    /**
     * Loads API configurations from local YAML file.
     */
    public void loadConfigsFromLocalYaml() {
        try {
            log.info("Loading API configurations from local YAML: {}", localConfigPath);

            Resource resource = resourceLoader.getResource(localConfigPath);
            if (!resource.exists()) {
                log.warn("Local config file not found: {}", localConfigPath);
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                ApiRateLimitConfig config = yamlMapper.readValue(inputStream, ApiRateLimitConfig.class);
                processApiEntries(config.getApis());
                log.info("Loaded {} API configurations from local YAML", config.getApis().size());
            }
        } catch (IOException e) {
            log.error("Failed to load API configurations from local YAML", e);
        }
    }

    /**
     * Process API entries from configuration and update rate limiter.
     */
    private void processApiEntries(List<ApiRateLimitConfig.ApiEntry> apiEntries) {
        if (apiEntries == null || apiEntries.isEmpty()) {
            log.warn("No API configurations found in config");
            return;
        }

        apiEntries.forEach(this::loadApiConfig);
    }

    /**
     * Loads a single API configuration from YAML.
     */
    private void loadApiConfig(ApiRateLimitConfig.ApiEntry apiEntry) {
        ApiConfig apiConfig = new ApiConfig();
        apiConfig.setId(apiEntry.getId());
        apiConfig.setBaseUrl(apiEntry.getBaseUrl());

        // Handle API key - either from config or from Secret Manager
        if (apiEntry.getApiKeySecretName() != null && !apiEntry.getApiKeySecretName().isEmpty()) {
            apiConfig.setApiKeySecretName(apiEntry.getApiKeySecretName());
            // Optionally load secret now
            if (isCloudEnvironment()) {
                try {
                    String apiKey = secretManagerService.getSecret(apiEntry.getApiKeySecretName());
                    apiConfig.setApiKey(apiKey);
                } catch (Exception e) {
                    log.warn("Could not load API key from Secret Manager: {}",
                            apiEntry.getApiKeySecretName(), e);
                    // Fallback to config API key if available
                    apiConfig.setApiKey(apiEntry.getApiKey());
                }
            } else {
                // In local development, use the key from config
                apiConfig.setApiKey(apiEntry.getApiKey());
            }
        } else {
            // No secret name, just use the API key from config
            apiConfig.setApiKey(apiEntry.getApiKey());
        }

        apiConfig.setLastUpdated(ZonedDateTime.now());

        List<RateLimit> rateLimits = apiEntry.getLimits().stream()
            .map(this::convertRateLimit)
            .collect(Collectors.toList());

        apiConfig.setRateLimits(rateLimits);
        rateLimiterService.updateApiConfig(apiConfig);

        log.debug("Loaded config for API: {} with {} rate limits",
            apiEntry.getBaseUrl(), rateLimits.size());
    }

    /**
     * Converts a YAML rate limit entry to a RateLimit model.
     */
    private RateLimit convertRateLimit(ApiRateLimitConfig.RateLimitEntry entry) {
        RateLimit rateLimit = new RateLimit();
        rateLimit.setId(entry.getId());
        rateLimit.setMaxCount(entry.getMaxCount());
        rateLimit.setTimeUnit(ChronoUnit.valueOf(entry.getTimeUnit()));
        return rateLimit;
    }

    /**
     * Manually reload configurations.
     * Can be called via REST endpoint or scheduled task.
     */
    public void reloadConfigurations() {
        log.info("Manually reloading API rate limit configurations");
        loadConfigs();
    }
}

