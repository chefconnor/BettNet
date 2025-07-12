package space.bettnet.sporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import space.bettnet.sporting.repository.GcpRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
@Component
public class DataIngestionService {

    private final BasketballDataService basketballDataService;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;
    private final GcpRepository gcpRepository;

    private final AtomicBoolean hasRun = new AtomicBoolean(false);

    // Batch size for files
    @Value("${data-ingestion-batch-size:2000}")
    private int batchSize;

    @Value("${data-ingestion-start-date:2022-01-01}")
    private String startDateStr;

    @Value("${data-ingestion-end-date:2024-12-31}")
    private String endDateStr;

    @Value("${data-ingestion-api-max-retries:3}")
    private int maxRetries;

    @Autowired
    public DataIngestionService(BasketballDataService basketballDataService,
                                ObjectMapper objectMapper,
                                GcpRepository gcpRepository) {
        this.basketballDataService = basketballDataService;
        this.objectMapper = objectMapper;
        this.gcpRepository = gcpRepository;

        // Configure retry template with exponential backoff
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .customBackoff(new ExponentialBackOffPolicy() {{
                    setInitialInterval(1000);
                    setMultiplier(2.0);
                    setMaxInterval(10000);
                }})
                .build();
    }

//    @Scheduled(fixedDelay = 60000)// Run at 1 AM daily
    public void scheduledIngestion() {
//        ingestAllDataToBucket();
        ingestSportRadarFullPipeline(); // New combined scheduled job
    }

    /**
     * Main method to ingest data from multiple sources and save to GCP bucket
     */
    public void ingestAllDataToBucket() {
        log.info("Starting full data ingestion to GCP bucket");

        try {

//            JsonNode books = basketballDataService.getSrBooksEndie();
//            JsonNode events = basketballDataService.getUpcomingEvents_TO();
//            // First get game data (this is required for the other APIs)
//            ingestGameDataForDateRange_RA();

            List<String> gameFiles = gcpRepository.listFilesWithWildcard("games/*/*/*.csv");

            List<String[]> p = new ArrayList<>();
            // Retrieve game IDs
            for (String gameFile : gameFiles) {
                p.addAll(gcpRepository.fetchCsvData(gameFile));
            }


            List<String> gameIds = retrieveSavedGameIds();


            if (!gameIds.isEmpty()) {
                // Process player statistics and play-by-play data asynchronously
                CompletableFuture<Void> playerStatsFuture = CompletableFuture.runAsync(() -> {
                    try {
                        ingestPlayerStatisticsForGames(gameIds);
                    } catch (Exception e) {
                        log.error("Error processing player statistics", e);
                    }
                });

                CompletableFuture<Void> playByPlayFuture = CompletableFuture.runAsync(() -> {
                    try {
                        ingestPlayByPlayForGames(gameIds);
                    } catch (Exception e) {
                        log.error("Error processing play-by-play data", e);
                    }
                });

                // Wait for all async tasks to complete
                CompletableFuture.allOf(playerStatsFuture, playByPlayFuture).join();
            }

            log.info("Full data ingestion completed successfully");

        } catch (Exception e) {
            log.error("Error during data ingestion", e);
        }
    }

    /**
     * Ingests daily schedule data from SportRadar for the configured date range.
     * Saves the data to GCP bucket under 'schedule_sr' directory.
     */
    // @Scheduled(cron = "0 15 0 * * ?") // Runs daily at 12:15 AM - REMOVE SCHEDULED, will be part of combined flow
    public void ingestDailyScheduleData_SR() {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        log.info("Ingesting SportRadar daily schedule data from {} to {}", startDate, endDate);

        List<LocalDate> dates = startDate.datesUntil(endDate.plusDays(1))
                .collect(Collectors.toList());

        for (int i = 0; i < dates.size(); i++) {
            final LocalDate date = dates.get(i);
            try {
                JsonNode scheduleData = retryTemplate.execute(context -> {
                    String year = String.valueOf(date.getYear());
                    String month = String.format("%02d", date.getMonthValue());
                    String day = String.format("%02d", date.getDayOfMonth());
                    return basketballDataService.getDailySchedule_SR(year, month, day);
                });

                if (scheduleData != null && scheduleData.has("games") && scheduleData.get("games").isArray()) {
                    ArrayNode gamesArray = (ArrayNode) scheduleData.get("games");
                    if (gamesArray.size() > 0) {
                        String year = String.valueOf(date.getYear());
                        String month = String.format("%02d", date.getMonthValue());
                        String day = String.format("%02d", date.getDayOfMonth());
                        String blobPath = String.format("schedule_sr/%s/%s/%s/schedule", year, month, day);
                        gcpRepository.saveDataAsCsv(gamesArray, "schedule_sr", blobPath);
                        log.info("Saved SportRadar daily schedule for date: {} [{}/{}] to {}", date, i + 1, dates.size(), blobPath);

                        // Stub for collecting gameIds from the ingested schedule data
                        List<String> gameIdsFromSchedule = new ArrayList<>();
                        for (JsonNode game : gamesArray) {
                            if (game.has("id")) { // Assuming 'id' is the field for game ID
                                gameIdsFromSchedule.add(game.get("id").asText());
                            }
                        }
                        // TODO: Develop further processing for these gameIdsFromSchedule
                        log.info("Collected {} game IDs from schedule {}: {}", gameIdsFromSchedule.size(), date, gameIdsFromSchedule);

                    }
                } else {
                    log.info("No schedule data or no games found for date: {} [{}/{}]", date, i + 1, dates.size());
                }
            } catch (Exception e) {
                log.error("Error ingesting SportRadar daily schedule data for date: " + date, e);
            }
        }
        log.info("Finished ingesting SportRadar daily schedule data.");
    }


    @Scheduled(initialDelay = 0, fixedDelay = 365 * 24 * 60 * 60 * 1000L) // every year
    public void ingestSportRadarFullPipeline() {
        log.info("Starting full SportRadar pipeline: Daily Schedule -> Game Summaries -> Player Profiles.");

        // Set the start date to January 4, 2022 to resume from where the process was interrupted
        LocalDate startDate = LocalDate.of(2022, 1, 4);
        LocalDate endDate = LocalDate.parse(endDateStr);

        log.info("Resuming SportRadar data ingestion from {} to {}", startDate, endDate);

        List<LocalDate> dates = startDate.datesUntil(endDate.plusDays(1)).collect(Collectors.toList());

        for (LocalDate date : dates) {
            log.info("Processing SportRadar data for date: {}", date);
            String year = String.valueOf(date.getYear());
            String month = String.format("%02d", date.getMonthValue());
            String day = String.format("%02d", date.getDayOfMonth());

            // Skip to the next date if we've already processed this one fully
            // This check would be more comprehensive with actual verification of completed files
            if (date.equals(LocalDate.of(2022, 1, 4))) {
                log.info("Date {} was partially processed before interruption, reprocessing it now", date);
            }

            List<String> gameIdsFromSchedule = new ArrayList<>();
            Set<String> allPlayerIdsForDate = new HashSet<>(); // Use Set for automatic duplicate removal

            // Step 1: Fetch and Store Daily Schedule, Extract Game IDs
            try {
                JsonNode scheduleData = retryTemplate.execute(context ->
                    basketballDataService.getDailySchedule_SR(year, month, day));

                if (scheduleData != null && scheduleData.has("games") && scheduleData.get("games").isArray()) {
                    ArrayNode gamesArray = (ArrayNode) scheduleData.get("games");
                    if (!gamesArray.isEmpty()) {
                        String scheduleBlobPath = String.format("schedule_sr/%s/%s/%s/schedule", year, month, day);
                        // Schedule data is typically one coherent set for the day, saved as one file.
                        // Batching here would mean splitting a single day's schedule which might not be desired.
                        // The existing batchSize applies to how many *rows* are processed if gamesArray was huge,
                        // but saveDataAsCsv writes the whole ArrayNode as one CSV.
                        gcpRepository.saveDataAsCsv(gamesArray, "schedule_sr", scheduleBlobPath);
                        log.info("Saved SportRadar daily schedule for date: {} to {} ({} games)", date, scheduleBlobPath, gamesArray.size());

                        for (JsonNode game : gamesArray) {
                            if (game.hasNonNull("id")) {
                                gameIdsFromSchedule.add(game.get("id").asText());
                            }
                        }
                        log.info("Extracted {} game IDs from schedule for date: {}", gameIdsFromSchedule.size(), date);
                    } else {
                        log.info("No games found in schedule data for date: {}", date);
                    }
                } else {
                    log.warn("No schedule data or games array not found for date: {}", date);
                }
            } catch (Exception e) {
                log.error("Error processing daily schedule for date: " + date, e);
                continue; // Skip to next date if schedule fetching fails
            }

            if (gameIdsFromSchedule.isEmpty()) {
                log.info("No game IDs from schedule for date {}, skipping game summaries and player profiles.", date);
                continue;
            }

            // Step 2: Fetch and Store Game Summaries (with batching), Extract Player IDs
            ArrayNode gameSummariesBatch = objectMapper.createArrayNode();
            int summaryCounter = 0;
            int summaryBatchNum = 1;
            for (int i = 0; i < gameIdsFromSchedule.size(); i++) {
                String gameId = gameIdsFromSchedule.get(i);
                try {
                    JsonNode gameSummaryData = retryTemplate.execute(context ->
                        basketballDataService.getGameSummary_SR(gameId));

                    if (gameSummaryData != null) {
                        gameSummariesBatch.add(gameSummaryData); // Add the whole summary as one record in the batch
                        summaryCounter++;
                        List<String> playerIdsFromSummary = extractPlayerIdsFromGameSummary(gameSummaryData);
                        if (!playerIdsFromSummary.isEmpty()) {
                            allPlayerIdsForDate.addAll(playerIdsFromSummary);
                        }
                    } else {
                        log.warn("No game summary data received for game ID: {}", gameId);
                    }
                } catch (Exception e) {
                    log.error("Error processing game summary for game ID: " + gameId, e);
                }

                // Save batch if it reaches batchSize or if it's the last game ID and there's data in the batch
                if (summaryCounter >= batchSize || (i == gameIdsFromSchedule.size() - 1 && summaryCounter > 0)) {
                    if (!gameSummariesBatch.isEmpty()) {
                        String summaryBatchPath = String.format("sr_gamedata/%s/%s/%s/summaries_batch_%d", year, month, day, summaryBatchNum);
                        gcpRepository.saveDataAsCsv(gameSummariesBatch, "sr_gamedata", summaryBatchPath);
                        log.info("Saved batch {} of game summaries for date {} ({} records) to {}", summaryBatchNum, date, gameSummariesBatch.size(), summaryBatchPath);
                        gameSummariesBatch = objectMapper.createArrayNode(); // Reset for next batch
                        summaryCounter = 0;
                        summaryBatchNum++;
                    }
                }
            }

            if (allPlayerIdsForDate.isEmpty()) {
                log.info("No player IDs extracted from game summaries for date {}, skipping player profiles.", date);
                continue;
            }

            // Step 3: Fetch and Store Player Profiles (with batching)
            ArrayNode playerProfilesBatch = objectMapper.createArrayNode();
            int profileCounter = 0;
            int profileBatchNum = 1;
            List<String> distinctPlayerIdsList = new ArrayList<>(allPlayerIdsForDate); // Convert Set to List for indexed iteration

            for (int i = 0; i < distinctPlayerIdsList.size(); i++) {
                String playerId = distinctPlayerIdsList.get(i);
                try {
                    JsonNode playerProfileData = retryTemplate.execute(context ->
                        basketballDataService.getPlayerProfile_SR(playerId));

                    if (playerProfileData != null) {
                        playerProfilesBatch.add(playerProfileData); // Add the whole profile as one record
                        profileCounter++;
                    } else {
                        log.warn("No player profile data received for player ID: {}", playerId);
                    }
                } catch (Exception e) {
                    log.error("Error ingesting player profile for player ID: " + playerId, e);
                }

                // Save batch if it reaches batchSize or if it's the last player ID and there's data
                if (profileCounter >= batchSize || (i == distinctPlayerIdsList.size() - 1 && profileCounter > 0)) {
                    if (!playerProfilesBatch.isEmpty()) {
                        String profileBatchPath = String.format("sr_playerprofile/%s/%s/%s/profiles_batch_%d", year, month, day, profileBatchNum);
                        gcpRepository.saveDataAsCsv(playerProfilesBatch, "sr_playerprofile", profileBatchPath);
                        log.info("Saved batch {} of player profiles for date {} ({} records) to {}", profileBatchNum, date, playerProfilesBatch.size(), profileBatchPath);
                        playerProfilesBatch = objectMapper.createArrayNode(); // Reset for next batch
                        profileCounter = 0;
                        profileBatchNum++;
                    }
                }
            }
            log.info("Finished processing SportRadar data for date: {}", date);
        }
        log.info("Completed full SportRadar pipeline: Daily Schedule -> Game Summaries -> Player Profiles.");
        log.info("BAILING OUT");
        throw new RuntimeException("BAILOUT");
    }

    private List<String> extractPlayerIdsFromGameSummary(JsonNode gameSummaryData) {
        List<String> playerIds = new ArrayList<>();
        if (gameSummaryData == null) return playerIds;

        // Example: Extracting from home players
        if (gameSummaryData.hasNonNull("home") && gameSummaryData.get("home").hasNonNull("players")) {
            for (JsonNode playerNode : gameSummaryData.get("home").get("players")) {
                if (playerNode.hasNonNull("id")) {
                    playerIds.add(playerNode.get("id").asText());
                }
            }
        }
        // Example: Extracting from away players
        if (gameSummaryData.hasNonNull("away") && gameSummaryData.get("away").hasNonNull("players")) {
            for (JsonNode playerNode : gameSummaryData.get("away").get("players")) {
                if (playerNode.hasNonNull("id")) {
                    playerIds.add(playerNode.get("id").asText());
                }
            }
        }
        // Add more extraction logic if players are nested differently (e.g., in rosters, statistics within summary)
        return playerIds;
    }

    /**
     * Ingest NBA game data for a date range with retry logic
     */
    public void ingestGameDataForDateRange_RA() {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        log.info("Ingesting game data from {} to {}", startDate, endDate);

        List<LocalDate> dates = startDate.datesUntil(endDate.plusDays(1))
                .collect(Collectors.toList());

        ArrayNode allGamesData = objectMapper.createArrayNode();
        int batchCounter = 0;
        int batchNumber = 1;
        int firstId = 0;
        int lastId = 0;

        // Process each date
        for (int i = 0; i < dates.size(); i++) {
            final LocalDate date = dates.get(i);
            try {
                // Use retry template for API calls
                JsonNode gamesData = retryTemplate.execute(context -> {
                    return basketballDataService.getNbaGamesByDate_RA(date);
                });
                // Add date information to each game and collect in the array
                if (gamesData.has("response") && gamesData.get("response").isArray()) {
                    ArrayNode games = (ArrayNode) gamesData.get("response");

                    for (JsonNode game : games) {
                        ObjectNode gameWithDate = (ObjectNode) game.deepCopy();
                        gameWithDate.put("retrieved_date", date.toString());
                        allGamesData.add(gameWithDate);
                        batchCounter++;
                    }
                }

                String dateStr = date.format(DateTimeFormatter.ISO_DATE);
                log.info("Collected games data for date: {} [{}/{}]", dateStr, i + 1, dates.size());

                // When we reach batch size or finish all dates, save as CSV
                if (batchCounter >= batchSize || i == dates.size() - 1) {
                    if (allGamesData.size() > 0) {
                        // Get first and last IDs for path naming
                        if (allGamesData.size() > 0) {
                            firstId = allGamesData.get(0).get("id").asInt();
                            lastId = allGamesData.get(allGamesData.size() - 1).get("id").asInt();
                        }

                        String batchPath = String.format("games/%s/%d/%d-%d", date, batchNumber, firstId, lastId);
                        gcpRepository.saveDataAsCsv(allGamesData, "games", batchPath);
                        log.info("Saved batch {} of games data with {} records", batchNumber, allGamesData.size());

                        // Reset for next batch
                        allGamesData = objectMapper.createArrayNode();
                        batchCounter = 0;
                        batchNumber++;
                    }
                }
            } catch (Exception e) {
                log.error("Error ingesting game data for date: " + date, e);
            }
        }
    }

    /**
     * Ingest player statistics for multiple games
     */
    public void ingestPlayerStatisticsForGames(List<String> gameIds) {
        log.info("Ingesting player statistics for {} games", gameIds.size());

        ArrayNode allPlayerStats = objectMapper.createArrayNode();
        int batchCounter = 0;
        int batchNumber = 1;

        for (int i = 0; i < gameIds.size(); i++) {
            String gameId = gameIds.get(i);
            try {
                JsonNode statsData = basketballDataService.getPlayerStatisticsByGame_RA(gameId);

                // Add game ID to each player stat and collect
                if (statsData.has("response") && statsData.get("response").isArray()) {
                    ArrayNode stats = (ArrayNode) statsData.get("response");
                    for (JsonNode stat : stats) {
                        ObjectNode statWithId = (ObjectNode) stat.deepCopy();
                        statWithId.put("game_id", gameId);
                        allPlayerStats.add(statWithId);
                        batchCounter++;
                    }
                }

                log.info("Collected player statistics for game: {} [{}/{}]", gameId, i + 1, gameIds.size());

                // When we reach batch size or finish all games, save as CSV
                if (batchCounter >= batchSize || i == gameIds.size() - 1) {
                    if (allPlayerStats.size() > 0) {
                        String batchPath = String.format("player-statistics/batch=%d/stats", batchNumber);
                        gcpRepository.saveDataAsCsv(allPlayerStats, "player_statistics", batchPath);
                        log.info("Saved batch {} of player statistics with {} records", batchNumber, allPlayerStats.size());

                        // Reset for next batch
                        allPlayerStats = objectMapper.createArrayNode();
                        batchCounter = 0;
                        batchNumber++;
                    }
                }
            } catch (Exception e) {
                log.error("Error ingesting player statistics for game: " + gameId, e);
            }
        }
    }

    /**
     * Ingest play-by-play data for multiple games
     */
    public void ingestPlayByPlayForGames(List<String> gameIds) {
        log.info("Ingesting play-by-play data for {} games", gameIds.size());

        ArrayNode allPlayByPlayData = objectMapper.createArrayNode();
        int batchCounter = 0;
        int batchNumber = 1;

        for (int i = 0; i < gameIds.size(); i++) {
            String gameId = gameIds.get(i);
            try {
                JsonNode playByPlayData = basketballDataService.getPlayByPlayData_SR(gameId);

                // Extract play-by-play events and collect them
                if (playByPlayData.has("plays") && playByPlayData.get("plays").isArray()) {
                    ArrayNode plays = (ArrayNode) playByPlayData.get("plays");
                    for (JsonNode play : plays) {
                        ObjectNode playWithId = (ObjectNode) play.deepCopy();
                        playWithId.put("game_id", gameId);
                        allPlayByPlayData.add(playWithId);
                        batchCounter++;
                    }
                }

                log.info("Collected play-by-play data for game: {} [{}/{}]", gameId, i + 1, gameIds.size());

                // When we reach batch size or finish all games, save as CSV
                if (batchCounter >= batchSize || i == gameIds.size() - 1) {
                    if (allPlayByPlayData.size() > 0) {
                        String batchPath = String.format("play-by-play/batch=%d/pbp", batchNumber);
                        gcpRepository.saveDataAsCsv(allPlayByPlayData, "play_by_play", batchPath);
                        log.info("Saved batch {} of play-by-play data with {} records", batchNumber, allPlayByPlayData.size());

                        // Reset for next batch
                        allPlayByPlayData = objectMapper.createArrayNode();
                        batchCounter = 0;
                        batchNumber++;
                    }
                }
            } catch (Exception e) {
                log.error("Error ingesting play-by-play data for game: " + gameId, e);
            }
        }
    }

    // Helper method to retrieve saved game IDs
    private List<String> retrieveSavedGameIds() {
        List<String> gameIds = new ArrayList<>();

        try {
            // Example approach - fetch the last week's games to get IDs
            LocalDate today = LocalDate.now();
            LocalDate oneWeekAgo = today.minus(7, ChronoUnit.DAYS);

            for (LocalDate date = oneWeekAgo; !date.isAfter(today); date = date.plusDays(1)) {
                JsonNode gamesData = basketballDataService.getNbaGamesByDate_RA(date);

                // Extract game IDs from the response
                if (gamesData.has("response")) {
                    for (JsonNode game : gamesData.get("response")) {
                        if (game.has("id")) {
                            gameIds.add(game.get("id").asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving game IDs", e);
        }

        return gameIds;
    }
}

