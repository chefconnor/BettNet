package space.bettnet.sporting.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import space.bettnet.sporting.config.RestClientConfig;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class BasketballDataService {

    private final RestClient rapidApiRestClient;
    private final RestClient sportRadarRestClient;
    private final RestClient developerSportRadarRestClient;
    private final RestClient theOddsApiRestClient;
    private final RestClientConfig restClientConfig;
    private ApiRateLimiterService rateLimiterService;
    ApiClientWrapper apiClientWrapper;
    private final long rapidApiWaitTimeMs = 1000; // 10 seconds between RapidAPI calls
    private final long sportRadarApiWaitTimeMs = 1500; // Increased to 15 seconds between SportRadar API calls
    private final long retryBackoffMs = 1000; // Increased to 10 seconds retry backoff when hitting rate limits

    private long lastRapidApiCallTime = 0;
    private long lastSportRadarApiCallTime = 0;

    @Autowired
    public BasketballDataService(
            RestClient rapidApiRestClient,
            RestClient sportRadarRestClient,
            @Qualifier("developerSportRadarRestClient") RestClient developerSportRadarRestClient,
            @Qualifier("theOddsApiRestClient") RestClient theOddsApiRestClient, // Ensure this bean exists
            RestClientConfig restClientConfig,
            ApiRateLimiterService rateLimiterService,
            ApiClientWrapper apiClientWrapper
    ) {
        this.rapidApiRestClient = rapidApiRestClient;
        this.sportRadarRestClient = sportRadarRestClient;
        this.developerSportRadarRestClient = developerSportRadarRestClient;
        this.theOddsApiRestClient = theOddsApiRestClient;
        this.restClientConfig = restClientConfig;
        this.rateLimiterService = rateLimiterService;
        this.apiClientWrapper = apiClientWrapper;
    }

    // Helper method to enforce rate limits
//    private void applyRateLimit(long lastCallTime, long waitTimeMs) {
//        if (waitTimeMs <= 0) return;
//        long currentTime = System.currentTimeMillis();
//        long elapsedTime = currentTime - lastCallTime;
//
//        // Only apply rate limiting if we've made a call recently and elapsed time is less than wait time
//        if (lastCallTime > 0 && elapsedTime < waitTimeMs) {
//            try {
//                long sleepTime = waitTimeMs - elapsedTime;
//                // Add a maximum sleep cap to prevent extremely long waits
//                sleepTime = Math.min(sleepTime, 5000); // Never wait more than 5 seconds
//                log.debug("Applying rate limit: sleeping for {} ms", sleepTime);
//                TimeUnit.MILLISECONDS.sleep(sleepTime);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                log.warn("Rate limiting sleep was interrupted", e);
//            }
//        }
//    }

    //rapidApi grouping
    private JsonNode callRapidApi(URI endpoint) {
//        applyRateLimit(lastRapidApiCallTime, rapidApiWaitTimeMs);
        try {
            JsonNode response = apiClientWrapper.executeWithRateLimit(restClientConfig.getRapidApiBaseUrl(), () ->
                    rapidApiRestClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .body(JsonNode.class)).get();
            lastRapidApiCallTime = System.currentTimeMillis();
            return response;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 429) { // Too Many Requests
                log.warn("Rate limit exceeded for RapidAPI, waiting before retry...");
                try {
                    TimeUnit.MILLISECONDS.sleep(retryBackoffMs);
                    return callRapidApi(endpoint); // Retry after waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("RapidAPI retry sleep interrupted", e);
                    throw ex; // Re-throw original exception after logging interruption
                }
            }
            throw ex;
        }
    }

    public JsonNode getPlayerStatisticsByGame_RA(String gameId) {
        return callRapidApi(UriComponentsBuilder.fromHttpUrl(restClientConfig.getRapidApiBaseUrl() + "/players/statistics")
                .queryParam("game", gameId)
                .build()
                .toUri());
    }

    /**
     * Get NBA games for a specific date from RapidAPI.
     * See <a href="https://rapidapi.com/api-sports/api/api-nba/playground/apiendpoint_f97c89e8-3b1f-4a34-b5b0-79725963b3c8">RapidAPI NBA Games Endpoint</a>.
     *
     * @param date LocalDate for the games
     * @return JsonNode containing games data
     */
    public JsonNode getNbaGamesByDate_RA(LocalDate date) {
        String formattedDate = date.format(DateTimeFormatter.ISO_DATE);
        return callRapidApi(UriComponentsBuilder.fromHttpUrl(restClientConfig.getRapidApiBaseUrl() + "/games")
                .queryParam("date", formattedDate)
                .build()
                .toUri());
    }

    //sportsRadar API grouping
    public JsonNode callSportsRadarApi(URI endpoint) {
//        applyRateLimit(lastSportRadarApiCallTime, sportRadarApiWaitTimeMs);
        try {
            JsonNode response = apiClientWrapper.executeWithRateLimit(restClientConfig.getSportRadarApiBaseUrl(), () ->
                    sportRadarRestClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .body(JsonNode.class)).get();
            lastSportRadarApiCallTime = System.currentTimeMillis();
            return response;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 429) { // Too Many Requests
                log.warn("Rate limit exceeded for SportRadar, waiting before retry...");
                try {
                    TimeUnit.MILLISECONDS.sleep(retryBackoffMs);
                    return callSportsRadarApi(endpoint); // Retry after waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("SportRadar retry sleep interrupted", e);
                    throw ex; // Re-throw original exception after logging interruption
                }
            }
            throw ex;
        }
    }

    public JsonNode getSchedule_SR() {
        // Assuming default values for season_year and season_type for demonstration
        return callSportsRadarApi(UriComponentsBuilder.fromHttpUrl(restClientConfig.getSportRadarApiBaseUrl() + "/nba/trial/v8/en/games/{season_year}/{season_type}/schedule.json")
                .buildAndExpand("2023", "REG").toUri());
    }

    public JsonNode getPlayByPlayData_SR(String gameId) {
        return callSportsRadarApi(UriComponentsBuilder.fromHttpUrl(restClientConfig.getSportRadarApiBaseUrl() + "/nba/trial/v8/en/games/{game_id}/pbp.json")
                .buildAndExpand(gameId).toUri());
    }

    /**
     * Get NBA daily schedule from SportRadar.
     * Endpoint: /nba/{access_level}/v8/{language_code}/games/{year}/{month}/{day}/schedule.json
     * Assumes access_level='trial', language_code='en'.
     *
     * @param year  The year of the schedule (e.g., "2023").
     * @param month The month of the schedule (e.g., "10").
     * @param day   The day of the schedule (e.g., "28").
     * @return JsonNode containing the daily schedule data.
     */
    public JsonNode getDailySchedule_SR(String year, String month, String day) {
        // Using "trial" for access_level and "en" for language_code as per common practice in this service
        // .json is used as the format
        return callSportsRadarApi(UriComponentsBuilder.fromHttpUrl(restClientConfig.getSportRadarApiBaseUrl() + "/nba/trial/v8/en/games/{year}/{month}/{day}/schedule.json")
                .buildAndExpand(year, month, day).toUri());
    }

    public JsonNode getPlayerProfile_SR(String playerId) {
        // Using "trial" for access_level and "en" for language_code
        // .json is used as the format
        return callSportsRadarApi(UriComponentsBuilder.fromHttpUrl(restClientConfig.getSportRadarApiBaseUrl() + "/nba/trial/v8/en/players/{player_id}/profile.json")
                .buildAndExpand(playerId).toUri());
    }

    /**
     * Get game summary from SportRadar.
     * Endpoint: /nba/{access_level}/v8/{language_code}/games/{game_id}/summary.{format}
     * Assumes access_level='trial', language_code='en', format='json'.
     *
     * @param gameId The ID of the game.
     * @return JsonNode containing the game summary data.
     */
    public JsonNode getGameSummary_SR(String gameId) {
        // Using "trial" for access_level, "en" for language_code, and "json" for format
        return callSportsRadarApi(UriComponentsBuilder.fromHttpUrl(restClientConfig.getSportRadarApiBaseUrl() + "/nba/trial/v8/en/games/{game_id}/summary.json")
                .buildAndExpand(gameId).toUri());
    }
//
//2025-05-31T13:23:40.399-04:00  INFO 41836 --- [sporting] [   scheduling-1] s.b.s.service.DataIngestionService       : Extracted 5 game IDs from schedule for date: 2022-01-04
//            2025-05-31T13:24:17.792-04:00  INFO 41836 --- [sporting] [   scheduling-1] s.b.sporting.repository.GcpRepository    : Successfully wrote CSV file with 5 records
//2025-05-31T13:24:18.133-04:00  INFO 41836 --- [sporting] [   scheduling-1] s.b.sporting.repository.GcpRepository    : Uploaded CSV file to sr_gamedata/2022/01/04/summaries_batch_1.csv
//2025-05-31T13:24:18.133-04:00  INFO 41836 --- [sporting] [   scheduling-1] s.b.s.service.DataIngestionService       : Saved batch 1 of game summaries for date 2022-01-04 (5 records) to sr_gamedata/2022/01/04/summaries_batch_1
//2025-05-31T13:30:40.569-04:00  WARN 41836 --- [sporting] [   scheduling-1] s.b.s.service.BasketballDataService      : Rate limit exceeded for SportRadar, waiting before retry...
//            2025-05-31T13:30:45.691-04:00  WARN 41836 --- [sporting] [   scheduling-1] s.b.s.service.BasketballDataService      : Rate limit exceeded for SportRadar, waiting before retry...
//            2025-05-31T13:30:50.820-04:00  WARN 41836 --- [sporting] [   scheduling-1] s.b.s.service.BasketballDataService      : Rate limit exceeded for SportRadar, waiting before retry...
//            2025-05-31T13:30:55.955-04:00  WARN 41836 --- [sporting] [   scheduling-1] s.b.s.service.BasketballDataService      : Rate limit exceeded for SportRadar, waiting before retry...
    // Developer SportRadar API grouping
}

