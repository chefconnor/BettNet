package space.bettnet.sporting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "api")
@Data
public class ApiRateLimitConfig {

    private List<ApiEntry> apis = new ArrayList<>();
    private String configBucket; // GCS bucket name for configs
    private String configPath; // Path within bucket for configs

    @Data
    public static class ApiEntry {
        private String id;
        private String baseUrl;
        private String apiKey;
        private String apiKeySecretName; // Google Secret name for API key
        private List<RateLimitEntry> limits = new ArrayList<>();
    }

    @Data
    public static class RateLimitEntry {
        private String id;
        private int maxCount;
        private String timeUnit; // Matches ChronoUnit values (e.g., "MINUTES", "DAYS")
    }
}
