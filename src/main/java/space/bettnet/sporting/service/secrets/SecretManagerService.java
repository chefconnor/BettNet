package space.bettnet.sporting.service.secrets;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for retrieving API keys and other secrets from Google Secret Manager.
 * Falls back to properties/environment variables in non-production environments.
 */
@Service
@Slf4j
public class SecretManagerService {

    private final Environment environment;

    // Cache secrets to avoid repeated lookups
    private final Map<String, String> secretCache = new HashMap<>();

    @Value("${gcp.project.id:bettnet-sporting}")
    private String projectId;

    @Value("${gcp.use-secret-manager:true}")
    private boolean useSecretManager;

    public SecretManagerService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Gets a secret value by name.
     * Uses Google Secret Manager in production environments.
     * Falls back to application properties in development/test environments.
     *
     * @param secretName the name of the secret
     * @return the secret value if found
     * @throws IOException if the secret cannot be retrieved
     */
    public String getSecret(String secretName) throws IOException {
        // Check cache first
        if (secretCache.containsKey(secretName)) {
            return secretCache.get(secretName);
        }

        String secretValue;

        // Use Secret Manager in production environments if enabled
        if (useSecretManager && !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            try {
                secretValue = getSecretFromSecretManager(secretName);
                log.debug("Retrieved secret {} from Google Secret Manager", secretName);
            } catch (Exception e) {
                log.warn("Failed to retrieve secret from Secret Manager, falling back to properties: {}", e.getMessage());
                secretValue = getSecretFromProperties(secretName);
            }
        } else {
            // Use properties in non-production environments
            secretValue = getSecretFromProperties(secretName);
            log.debug("Retrieved secret {} from application properties", secretName);
        }

        // Cache for future lookups
        secretCache.put(secretName, secretValue);

        return secretValue;
    }

    /**
     * Retrieves a secret from Google Secret Manager.
     *
     * @param secretName Name of the secret
     * @return Secret value
     * @throws IOException If an error occurs accessing Secret Manager
     */
    private String getSecretFromSecretManager(String secretName) throws IOException {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretName, "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            return response.getPayload().getData().toStringUtf8();
        }
    }

    /**
     * Retrieves a secret from application properties or environment variables.
     *
     * @param secretName Name of the secret
     * @return Secret value
     * @throws IOException If the secret is not found
     */
    private String getSecretFromProperties(String secretName) throws IOException {
        // First check with original name
        String secretValue = environment.getProperty(secretName);

        if (secretValue == null || secretValue.isEmpty()) {
            // Try with API_KEY_ prefix and uppercase with underscores
            String envVarName = "API_KEY_" + secretName.toUpperCase().replace('-', '_');
            secretValue = environment.getProperty(envVarName);
        }

        if (secretValue == null || secretValue.isEmpty()) {
            // Also check application-dev.properties for dev environment
            secretValue = environment.getProperty(secretName + ".dev");
        }

        if (secretValue == null || secretValue.isEmpty()) {
            log.warn("No value found for secret: {}", secretName);
            throw new IOException("Secret not found: " + secretName);
        }

        return secretValue;
    }
}

