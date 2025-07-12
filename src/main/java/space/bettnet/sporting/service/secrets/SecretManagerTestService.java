package space.bettnet.sporting.service.secrets;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import lombok.Setter;

@Service
@Slf4j
public class SecretManagerTestService {

    @Setter
    @Value("${gcp.project.id}")
    private String projectId;

    @Setter
    @Value("${gcp.secret.credentials-id}")
    private String secretId;

    /**
     * Test method to verify Secret Manager access from local environment
     * Only runs when the "test-secrets" profile is active
     */
    @Bean
    @Profile("test-secrets")
    public CommandLineRunner testSecretAccess() {
        return args -> {
            try {
                log.info("Testing access to Secret Manager...");
                String secretValue = accessSecret(projectId, secretId);

                // Don't log the full secret in production, this is just for testing
                log.info("Successfully retrieved secret: {}... (first 20 chars)",
                         secretValue.substring(0, Math.min(secretValue.length(), 20)));

                log.info("Secret Manager access test successful!");
            } catch (Exception e) {
                log.error("Failed to access secret from Secret Manager", e);
            }
        };
    }

    /**
     * Access a secret from Google Secret Manager
     */
    public String accessSecret(String projectId, String secretId) throws Exception {
        // Create the Secret Manager client
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretId, "latest");

            // Access the secret version
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);

            // Return the secret payload as a string
            return response.getPayload().getData().toStringUtf8();
        }
    }
}
