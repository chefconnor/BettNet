package space.bettnet.sporting.model.ratelimit;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for an API with rate limits.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiConfig {
    private String id;
    private String baseUrl;
    private String apiKey;
    private String apiKeySecretName; // Google Secret name for API key

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private ZonedDateTime lastUpdated;

    @Builder.Default
    private List<RateLimit> rateLimits = new ArrayList<>();
}

