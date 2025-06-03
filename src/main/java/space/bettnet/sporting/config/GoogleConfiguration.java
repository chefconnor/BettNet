package space.bettnet.sporting.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@Getter
@Slf4j
public class GoogleConfiguration {

    @Value("${gcp.project.id:bettnet-sporting}")
    private String projectId;

    @Value("${gcp-bucket-name:sporting-data-lake}")
    private String bucketName;

    @Value("${gcp.credentials.path:gcp-credentials.json}")
    private String credentialsPath;

    @Value("${gcp.region:us-central1}")
    private String region;

    @Value("${gcp.secret.credentials-id:sporting-service-account-key}")
    private String credentialsSecretId;

    @Value("${gcp.secret.credentials-version:latest}")
    private String credentialsSecretVersion;

    @Value("${gcp.use-secret-manager:true}")
    private boolean useSecretManager;

    /**
     * Creates a Google Cloud Storage client using the application's credentials
     *
     * @return Storage client instance
     * @throws IOException if credentials file cannot be loaded
     */
    @Bean
    public Storage googleCloudStorage() throws IOException {
        GoogleCredentials credentials = getCredentials();

        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(credentials)
                .build()
                .getService();
    }

    /**
     * Loads Google credentials from Secret Manager or local file
     *
     * @return GoogleCredentials instance
     * @throws IOException if credentials cannot be loaded
     */
    private GoogleCredentials getCredentials() throws IOException {
        if (useSecretManager) {
            try {
                log.info("Attempting to retrieve credentials from Secret Manager");
                return getCredentialsFromSecretManager();
            } catch (Exception e) {
                log.warn("Failed to retrieve credentials from Secret Manager: {}. Falling back to local credentials file.", e.getMessage());
                // Fall through to try local file instead
            }
        }

        try {
            // Try loading from classpath resource
            log.info("Attempting to load credentials from classpath resource: {}", credentialsPath);
            InputStream credentialsStream = new ClassPathResource(credentialsPath).getInputStream();
            return GoogleCredentials.fromStream(credentialsStream);
        } catch (IOException e) {
            log.warn("Failed to load credentials from classpath resource: {}. Falling back to application default credentials.", e.getMessage());
            // Fall back to application default credentials
            log.info("Attempting to use application default credentials");
            return GoogleCredentials.getApplicationDefault();
        }
    }

    /**
     * Retrieves credentials from Google Secret Manager
     *
     * @return GoogleCredentials loaded from Secret Manager
     * @throws IOException if credentials cannot be retrieved or parsed
     */
    private GoogleCredentials getCredentialsFromSecretManager() throws IOException {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(
                    projectId,
                    credentialsSecretId,
                    credentialsSecretVersion);

            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            String secretData = response.getPayload().getData().toStringUtf8();

            // Parse the JSON credentials
            return GoogleCredentials.fromStream(new ByteArrayInputStream(secretData.getBytes()));
        } catch (Exception e) {
            throw new IOException("Failed to retrieve credentials from Secret Manager: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the full GCS URI for a given object path
     *
     * @param objectPath the path within the bucket
     * @return full GCS URI (gs://bucket-name/object-path)
     */
    public String getBucketObjectUri(String objectPath) {
        return String.format("gs://%s/%s", bucketName, objectPath);
    }
}
