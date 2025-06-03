package space.bettnet.sporting.service.secrets;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to verify that configuration values are being loaded from Google Secret Manager
 * rather than using the default values in the @Value annotations
 */
@Service
@Slf4j
public class SecretVerificationService {

    @Autowired
    private Environment env;

    /**
     * This method runs when the application starts with the "verify-secrets" profile
     * and logs the actual values loaded from configuration to verify they're from Secret Manager
     */
    @Bean
    @Profile("verify-secrets")
    public CommandLineRunner verifySecretValues() {
        return args -> {
            log.info("🔍 VERIFYING CONFIGURATION VALUES LOADED 🔍");
            log.info("-------------------------------------");

            // Properties to check - add the ones you know should be in Secret Manager
            String[] propertiesToCheck = {
                "gcp.project.id",
                "spring.cloud.gcp.project-id",
                "gcp.secret.credentials.id",
                "rapid.api.host",
                "rapid.api.key",
                "sportradar.api.key",
                "theodds.api.key"
            };

            Map<String, String> propertyValues = new HashMap<>();

            for (String property : propertiesToCheck) {
                String value = env.getProperty(property);
                propertyValues.put(property, value);

                if (value != null) {
                    // For sensitive values, mask them
                    if (property.toLowerCase().contains("key") || property.toLowerCase().contains("secret")) {
                        log.info("{}: {}", property, maskSecret(value));
                    } else {
                        log.info("{}: {}", property, value);
                    }
                } else {
                    log.warn("{}: ⚠️ NOT FOUND", property);
                }
            }

            // Check if we're getting any values from Secret Manager
            boolean foundProperties = propertyValues.values().stream().anyMatch(v -> v != null);

            if (foundProperties) {
                log.info("✅ Some properties were successfully loaded!");
                log.info("Check if they match your expected values from Secret Manager");
            } else {
                log.error("❌ No properties were found! Secret Manager integration may not be working.");
            }

            // List all active profiles
            log.info("Active profiles: {}", Arrays.toString(env.getActiveProfiles()));

            log.info("-------------------------------------");
            log.info("Now, let's try to load a specific secret directly from Secret Manager...");

            try {
                SecretManagerTestService testService = new SecretManagerTestService();
                testService.setProjectId(env.getProperty("gcp.project.id", "bettnet-sporting"));
                testService.setSecretId(env.getProperty("gcp.secret.credentials-id", "sporting-service-account-key"));

                String secretValue = testService.accessSecret(
                    env.getProperty("gcp.project.id", "bettnet-sporting"),
                    env.getProperty("gcp.secret.credentials-id", "sporting-service-account-key")
                );

                log.info("Successfully retrieved secret: {}... (first 20 chars)",
                         secretValue.substring(0, Math.min(secretValue.length(), 20)));
                log.info("Secret Manager direct access test successful!");
            } catch (Exception e) {
                log.error("Failed to access secret directly from Secret Manager", e);
            }
        };
    }

    /**
     * Mask a secret by showing only first 4 and last 4 characters
     */
    private String maskSecret(String secret) {
        if (secret == null || secret.length() <= 8) {
            return "[MASKED]";
        }
        return secret.substring(0, 4) + "..." + secret.substring(secret.length() - 4);
    }
}

