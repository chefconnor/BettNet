package space.bettnet.sporting.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.api.gax.paging.Page;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Blob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.nio.channels.Channels;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Repository
@Slf4j
public class GcpRepository {

    private final Storage storage;
    private final String bucketName;
    private final ObjectMapper objectMapper;

    // Temporary directory for files before upload
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    @Autowired
    public GcpRepository(Storage storage,
                         @Value("${gcp-bucket-name}") String bucketName,
                         ObjectMapper objectMapper) {
        this.storage = storage;
        this.bucketName = bucketName;
        this.objectMapper = objectMapper;
    }

    /**
     * Save data as CSV file format to GCP Storage
     *
     * @param jsonData The JSON data to convert to CSV
     * @param dataType Type of data being saved (for schema creation)
     * @param blobPath Path to save in GCP bucket
     * @return The path where the file was saved
     */
    public String saveDataAsCsv(ArrayNode jsonData, String dataType, String blobPath) {
        try {
            // Create a temporary file
            String tempFilePath = TEMP_DIR + File.separator + UUID.randomUUID() + ".csv";
            File tempFile = new File(tempFilePath);

            // Create CSV writer
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                // Write header
                Set<String> headers = new LinkedHashSet<>();
                for (JsonNode node : jsonData) {
                    headers.addAll(flattenJson(node).keySet());
                }
                writer.write(String.join(",", headers));
                writer.newLine();

                // Write data
                for (JsonNode node : jsonData) {
                    Map<String, String> flattened = flattenJson(node);
                    List<String> values = headers.stream()
                            .map(header -> flattened.getOrDefault(header, ""))
                            .collect(Collectors.toList());
                    writer.write(String.join(",", values));
                    writer.newLine();
                }
            }

            log.info("Successfully wrote CSV file with {} records", jsonData.size());

            // Upload to Google Cloud Storage
            try (FileInputStream fileInputStream = new FileInputStream(tempFile)) {
                String finalPath = blobPath.endsWith(".csv") ? blobPath : blobPath + ".csv";
                BlobId blobId = BlobId.of(bucketName, finalPath);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                        .setContentType("text/csv")
                        .build();

                storage.create(blobInfo, fileInputStream);
                log.info("Uploaded CSV file to {}", finalPath);

                // Clean up temporary file
                tempFile.delete();

                return finalPath;
            }
        } catch (Exception e) {
            log.error("Error saving data as CSV: " + e.getMessage(), e);
            throw new RuntimeException("Failed to save data to GCP", e);
        }
    }


    /**
     * List files in GCP Storage with wildcard support
     *
     * @param pattern The pattern with optional wildcards (*)
     * @return List of matching file paths
     */
    public List<String> listFilesWithWildcard(String pattern) {
        // Convert pattern to regex
        String regex = pattern
                .replace(".", "\\.")  // Escape dots
                .replace("*", ".*");  // Convert * to regex .*

        // Find the prefix (part before first wildcard)
        String prefix = pattern.contains("*") ?
                pattern.substring(0, pattern.indexOf('*')) :
                pattern;

        // List all blobs with this prefix
        Page<Blob> blobs = storage.list(bucketName,
                Storage.BlobListOption.prefix(prefix));

        // Filter results that match the regex pattern
        return StreamSupport.stream(blobs.iterateAll().spliterator(), false)
                .map(Blob::getName)
                .filter(name -> name.matches(regex))
                .collect(Collectors.toList());
    }
    /**
     * Fetch a CSV file from GCP Storage and return its content
     *
     * @param blobPath The path of the file in GCP bucket
     * @return The content of the CSV file as a List of String arrays (rows and columns)
     */
    public List<String[]> fetchCsvData(String blobPath) {
        try {
            Page<Blob> ff = storage.list(bucketName);
            Blob blob = storage.get(BlobId.of(bucketName, blobPath));
            if (blob == null) {
                throw new FileNotFoundException("File not found: " + blobPath);
            }

            List<String[]> csvData = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    Channels.newReader(blob.reader(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Simple CSV parsing - could use a CSV library for more complex cases
                    String[] values = line.split(",");
                    csvData.add(values);
                }
            }

            log.info("Successfully fetched CSV file from {}", blobPath);
            return csvData;

        } catch (Exception e) {
            log.error("Error fetching CSV data: " + e.getMessage(), e);
            throw new RuntimeException("Failed to fetch data from GCP", e);
        }
    }

    /**
     * Fetch a CSV file containing daily schedule data from GCP Storage and return its content.
     * This is specifically for data ingested from SportRadar daily schedule.
     *
     * @param blobPath The path of the file in GCP bucket (e.g., schedule_sr/2023/05/31/schedule.csv)
     * @return The content of the CSV file as a List of String arrays (rows and columns)
     */
    public List<String[]> fetchDailyScheduleCsvData(String blobPath) {
        // For now, this method behaves identically to fetchCsvData.
        // It can be customized later if schedule data requires different handling.
        log.info("Fetching daily schedule CSV data from: {}", blobPath);
        return fetchCsvData(blobPath); // Delegates to the generic fetchCsvData
    }

    /**
     * List all files in a specific folder in GCP Storage
     *
     * @param folderPath The folder path in GCP bucket
     * @return List of file paths
     */
    public List<String> listFiles(String folderPath) {
        String prefix = folderPath.endsWith("/") ? folderPath : folderPath + "/";

        Iterable<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefix))
                .iterateAll();

        return StreamSupport.stream(blobs.spliterator(), false)
                .map(Blob::getName)
                .collect(Collectors.toList());
    }

    /**
     * Flatten JSON object to a map with dot notation for nested fields
     *
     * @param node The JSON node to flatten
     * @return Flattened map
     */
    private Map<String, String> flattenJson(JsonNode node) {
        Map<String, String> flattened = new LinkedHashMap<>();
        flattenJsonHelper(node, "", flattened);
        return flattened;
    }

    private void flattenJsonHelper(JsonNode node, String prefix, Map<String, String> flattened) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String newPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flattenJsonHelper(entry.getValue(), newPrefix, flattened);
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                String newPrefix = prefix + "[" + i + "]";
                flattenJsonHelper(node.get(i), newPrefix, flattened);
            }
        } else {
            flattened.put(prefix, node.asText());
        }
    }

    /**
     * Get all game summaries for a specific date
     *
     * @param year Year (e.g., "2022")
     * @param month Month (e.g., "01" for January)
     * @param day Day (e.g., "04")
     * @return Map of game IDs to their summary data
     */
    public Map<String, List<String[]>> getGameSummaries(String year, String month, String day) {
        Map<String, List<String[]>> results = new HashMap<>();

        // Format the prefix for the given date
        String prefix = String.format("sr_gamedata/%s/%s/%s/", year, month, day);

        // Find all CSV files for game summaries on this date
        List<String> summaryFiles = listFilesWithWildcard(prefix + "*.csv");

        for (String file : summaryFiles) {
            // Extract game ID or batch info from filename
            String fileId = file.substring(file.lastIndexOf("/") + 1).replace(".csv", "");
            List<String[]> csvData = fetchCsvData(file);
            results.put(fileId, csvData);
        }

        log.info("Retrieved {} game summary files for date {}-{}-{}", results.size(), year, month, day);
        return results;
    }

    /**
     * Get all player profiles collected on a specific date
     *
     * @param year Year (e.g., "2022")
     * @param month Month (e.g., "01" for January)
     * @param day Day (e.g., "04")
     * @return Map of player IDs/batches to their profile data
     */
    public Map<String, List<String[]>> getPlayerProfiles(String year, String month, String day) {
        Map<String, List<String[]>> results = new HashMap<>();

        // Format the prefix for the given date
        String prefix = String.format("sr_playerprofile/%s/%s/%s/", year, month, day);

        // Find all CSV files for player profiles on this date
        List<String> profileFiles = listFilesWithWildcard(prefix + "*.csv");

        for (String file : profileFiles) {
            // Extract player ID or batch info from filename
            String fileId = file.substring(file.lastIndexOf("/") + 1).replace(".csv", "");
            List<String[]> csvData = fetchCsvData(file);
            results.put(fileId, csvData);
        }

        log.info("Retrieved {} player profile files for date {}-{}-{}", results.size(), year, month, day);
        return results;
    }

    /**
     * Get the schedule data for a specific date
     *
     * @param year Year (e.g., "2022")
     * @param month Month (e.g., "01" for January)
     * @param day Day (e.g., "04")
     * @return The schedule data as rows and columns
     */
    public List<String[]> getScheduleData(String year, String month, String day) {
        String schedulePath = String.format("schedule_sr/%s/%s/%s/schedule.csv", year, month, day);
        try {
            return fetchDailyScheduleCsvData(schedulePath);
        } catch (Exception e) {
            log.warn("Could not find schedule for date {}-{}-{}: {}", year, month, day, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get all game data for a specific date
     *
     * @param year Year as string (e.g., "2022")
     * @param month Month as string (e.g., "01" for January)
     * @param day Day as string (e.g., "04")
     * @return Map of batch IDs to game data
     */
    public Map<String, List<String[]>> getGameData(String year, String month, String day) {
        Map<String, List<String[]>> results = new HashMap<>();

        // Format the prefix for the given date
        String dateStr = year + "-" + month + "-" + day;
        String prefix = String.format("games/%s/", dateStr);

        // Find all CSV files for game data on this date
        List<String> gameDataFiles = listFilesWithWildcard(prefix + "**/*.csv");

        for (String file : gameDataFiles) {
            // Extract batch info from filename
            String fileId = file.substring(file.lastIndexOf("/") + 1).replace(".csv", "");
            List<String[]> csvData = fetchCsvData(file);
            results.put(fileId, csvData);
        }

        log.info("Retrieved {} game data files for date {}", results.size(), dateStr);
        return results;
    }

    /**
     * Get all schedule data within a date range
     *
     * @param startDate Start date in format "YYYY-MM-DD"
     * @param endDate End date in format "YYYY-MM-DD"
     * @return Map of date strings to schedule data
     */
    public Map<String, List<String[]>> getScheduleDataRange(String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        Map<String, List<String[]>> results = new HashMap<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String year = String.valueOf(date.getYear());
            String month = String.format("%02d", date.getMonthValue());
            String day = String.format("%02d", date.getDayOfMonth());

            try {
                List<String[]> scheduleData = getScheduleData(year, month, day);
                if (!scheduleData.isEmpty()) {
                    results.put(date.toString(), scheduleData);
                }
            } catch (Exception e) {
                log.debug("No schedule data for date {}: {}", date, e.getMessage());
            }
        }

        log.info("Retrieved schedule data for {} dates between {} and {}",
                results.size(), startDate, endDate);
        return results;
    }

    /**
     * Get player profile by specific player ID
     *
     * @param playerId The ID of the player to retrieve
     * @return Player profile data or empty list if not found
     */
    public List<String[]> getPlayerProfileById(String playerId) {
        // Use wildcard search to find this player ID across any date
        List<String> profileFiles = listFilesWithWildcard("sr_playerprofile/**/" + playerId + "_profile.csv");

        if (!profileFiles.isEmpty()) {
            // Return the first match if found (presumably the most recent)
            String mostRecentFile = profileFiles.get(profileFiles.size() - 1);
            return fetchCsvData(mostRecentFile);
        }

        // If not found using exact filename, search profiles_batch files
        List<String> batchFiles = listFilesWithWildcard("sr_playerprofile/**/profiles_batch_*.csv");

        for (String batchFile : batchFiles) {
            List<String[]> csvData = fetchCsvData(batchFile);
            if (csvData.size() > 1) {
                // Check headers to find the ID column
                String[] headers = csvData.get(0);
                int idColumnIndex = -1;

                for (int i = 0; i < headers.length; i++) {
                    if (headers[i].equals("id") || headers[i].endsWith(".id")) {
                        idColumnIndex = i;
                        break;
                    }
                }

                if (idColumnIndex >= 0) {
                    // Search rows for matching player ID
                    for (int i = 1; i < csvData.size(); i++) {
                        String[] row = csvData.get(i);
                        if (row.length > idColumnIndex && playerId.equals(row[idColumnIndex])) {
                            // Return this row with headers as the first row
                            return Arrays.asList(headers, row);
                        }
                    }
                }
            }
        }

        log.warn("No player profile found for ID: {}", playerId);
        return Collections.emptyList();
    }

    /**
     * Get game summary by specific game ID
     *
     * @param gameId The ID of the game to retrieve
     * @return Game summary data or empty list if not found
     */
    public List<String[]> getGameSummaryById(String gameId) {
        // Try to find a dedicated file for this game ID
        List<String> summaryFiles = listFilesWithWildcard("sr_gamedata/**/" + gameId + "_summary.csv");

        if (!summaryFiles.isEmpty()) {
            // Return the first match if found
            String mostRecentFile = summaryFiles.get(summaryFiles.size() - 1);
            return fetchCsvData(mostRecentFile);
        }

        // If not found using exact filename, search summaries_batch files
        List<String> batchFiles = listFilesWithWildcard("sr_gamedata/**/summaries_batch_*.csv");

        for (String batchFile : batchFiles) {
            List<String[]> csvData = fetchCsvData(batchFile);
            if (csvData.size() > 1) {
                // Check headers to find ID column
                String[] headers = csvData.get(0);
                int idColumnIndex = -1;

                for (int i = 0; i < headers.length; i++) {
                    if (headers[i].equals("id") || headers[i].endsWith(".id")) {
                        idColumnIndex = i;
                        break;
                    }
                }

                if (idColumnIndex >= 0) {
                    // Search rows for matching game ID
                    for (int i = 1; i < csvData.size(); i++) {
                        String[] row = csvData.get(i);
                        if (row.length > idColumnIndex && gameId.equals(row[idColumnIndex])) {
                            // Return this row with headers as first row
                            return Arrays.asList(headers, row);
                        }
                    }
                }
            }
        }

        log.warn("No game summary found for ID: {}", gameId);
        return Collections.emptyList();
    }
}
