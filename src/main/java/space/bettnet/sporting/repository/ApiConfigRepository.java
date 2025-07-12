package space.bettnet.sporting.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import space.bettnet.sporting.config.ApiRateLimitConfig;
import space.bettnet.sporting.model.ratelimit.ApiConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository for managing API rate limit configurations in Google Cloud Storage.
 * Uses a single YAML file for all configurations.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ApiConfigRepository {

    private final Storage storage;
    private final ObjectMapper jsonObjectMapper;

    // Create a YAML mapper for reading/writing YAML files
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Value("${gcp.bucket.name}")
    private String bucketName;

    // Single file path for all API configurations
    private static final String CONFIG_FILE_PATH = "rate-limits.yml";

    // Cache of API configurations
    private Map<String, ApiConfig> configCache = new HashMap<>();
    private boolean cacheLoaded = false;

    /**
     * Saves an API configuration to Google Cloud Storage.
     * Updates the single YAML file with all configurations.
     *
     * @param apiConfig The API configuration to save
     * @return The saved API configuration
     */
    public ApiConfig save(ApiConfig apiConfig) {
        try {
            apiConfig.setLastUpdated(ZonedDateTime.now());

            // Ensure cache is loaded
            if (!cacheLoaded) {
                loadAllConfigs();
            }

            // Update the cache with the new/updated config
            configCache.put(apiConfig.getBaseUrl(), apiConfig);

            // Save all configs back to the single file
            saveAllConfigs();

            log.debug("Saved API config for {} to GCS in single YAML file", apiConfig.getBaseUrl());
            return apiConfig;
        } catch (Exception e) {
            log.error("Failed to save API config to GCS", e);
            throw new RuntimeException("Failed to save API config to GCS", e);
        }
    }

    /**
     * Save all configurations to the single YAML file.
     */
    private void saveAllConfigs() throws IOException {
        // Convert the map of ApiConfigs to a structure that matches our YAML file
        ApiRateLimitConfig config = new ApiRateLimitConfig();
        config.setConfigBucket(bucketName);
        config.setConfigPath("api-rate-limits");

        // Convert our internal ApiConfig objects to the format expected by the YAML file
        List<ApiRateLimitConfig.ApiEntry> apiEntries = configCache.values().stream()
            .map(this::convertToApiEntry)
            .collect(Collectors.toList());

        config.setApis(apiEntries);

        // Convert to YAML and save to GCS
        String yamlContent = yamlMapper.writeValueAsString(config);

        BlobId blobId = BlobId.of(bucketName, CONFIG_FILE_PATH);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("application/yaml").build();

        storage.create(blobInfo, yamlContent.getBytes(StandardCharsets.UTF_8));
        log.debug("Saved all API configurations to single YAML file in GCS");
    }

    /**
     * Convert our internal ApiConfig to the YAML file structure.
     */
    private ApiRateLimitConfig.ApiEntry convertToApiEntry(ApiConfig apiConfig) {
        ApiRateLimitConfig.ApiEntry entry = new ApiRateLimitConfig.ApiEntry();
        entry.setId(apiConfig.getId());
        entry.setBaseUrl(apiConfig.getBaseUrl());
        entry.setApiKeySecretName(apiConfig.getApiKeySecretName());

        List<ApiRateLimitConfig.RateLimitEntry> limitEntries = apiConfig.getRateLimits().stream()
            .map(limit -> {
                ApiRateLimitConfig.RateLimitEntry limitEntry = new ApiRateLimitConfig.RateLimitEntry();
                limitEntry.setId(limit.getId());
                limitEntry.setMaxCount(limit.getMaxCount());
                limitEntry.setTimeUnit(limit.getTimeUnit().toString());
                return limitEntry;
            })
            .collect(Collectors.toList());

        entry.setLimits(limitEntries);
        return entry;
    }

    /**
     * Retrieves an API configuration from cache or loads all configs if needed.
     *
     * @param baseUrl The base URL of the API
     * @return Optional containing the API configuration if found
     */
    public Optional<ApiConfig> findByBaseUrl(String baseUrl) {
        // Ensure cache is loaded
        if (!cacheLoaded) {
            loadAllConfigs();
        }

        return Optional.ofNullable(configCache.get(baseUrl));
    }

    /**
     * Loads all API configurations from the single YAML file in GCS.
     * Populates the internal cache.
     *
     * @return List of all API configurations
     */
    public List<ApiConfig> findAll() {
        loadAllConfigs();
        return new ArrayList<>(configCache.values());
    }

    /**
     * Load all configurations from the single YAML file.
     */
    private void loadAllConfigs() {
        try {
            BlobId blobId = BlobId.of(bucketName, CONFIG_FILE_PATH);
            Blob blob = storage.get(blobId);

            if (blob == null || !blob.exists()) {
                log.debug("No configuration file found in GCS");
                configCache = new HashMap<>();
                cacheLoaded = true;
                return;
            }

            String yamlContent = new String(blob.getContent(), StandardCharsets.UTF_8);
            ApiRateLimitConfig config = yamlMapper.readValue(yamlContent, ApiRateLimitConfig.class);

            // Convert the YAML structure to our internal ApiConfig objects
            configCache = config.getApis().stream()
                .map(this::convertToApiConfig)
                .collect(Collectors.toMap(ApiConfig::getBaseUrl, apiConfig -> apiConfig));

            cacheLoaded = true;
            log.debug("Loaded {} API configurations from single YAML file in GCS", configCache.size());
        } catch (IOException e) {
            log.error("Failed to load API configurations from GCS", e);
            configCache = new HashMap<>();
            cacheLoaded = true;
        }
    }

    /**
     * Convert YAML file entry to our internal ApiConfig.
     */
    private ApiConfig convertToApiConfig(ApiRateLimitConfig.ApiEntry entry) {
        ApiConfig apiConfig = new ApiConfig();
        apiConfig.setId(entry.getId());
        apiConfig.setBaseUrl(entry.getBaseUrl());
        apiConfig.setApiKeySecretName(entry.getApiKeySecretName());
        apiConfig.setLastUpdated(ZonedDateTime.now());

        // Convert the rate limits
        List<space.bettnet.sporting.model.ratelimit.RateLimit> limits = entry.getLimits().stream()
            .map(limitEntry -> {
                space.bettnet.sporting.model.ratelimit.RateLimit limit = new space.bettnet.sporting.model.ratelimit.RateLimit();
                limit.setId(limitEntry.getId());
                limit.setMaxCount(limitEntry.getMaxCount());
                limit.setTimeUnit(java.time.temporal.ChronoUnit.valueOf(limitEntry.getTimeUnit()));
                return limit;
            })
            .collect(Collectors.toList());

        apiConfig.setRateLimits(limits);
        return apiConfig;
    }
}
