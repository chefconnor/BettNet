package space.bettnet.sporting.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // RapidAPI configuration
    @Value("${rapid.api.host:api-nba-v1.p.rapidapi.com}")
    private String rapidApiHost;
    @Value("${rapid.api.key:c7bcafd4b8mshc5fdb47579d45c1p109d61jsn6d9df046f6c7}")
    private String rapidApiKey;
    @Value("${rapid.api.base.url:https://api-nba-v1.p.rapidapi.com}")
    private String rapidApiBaseUrl;

    // Sportradar configuration
    @Value("${sportradar-api-host:api.sportradar.com}")
    private String sportradarApiHost;
    @Value("${sportradar-api-key:${SPORTRADAR_API_KEY:}}") // Allow env variable as fallback
    private String sportradarApiKey;
    @Value("${sportradar-api-base-url:https://api.sportradar.com}")
    private String sportradarApiBaseUrl;

    // Sportradar Developer Portal configuration (new)
    @Value("${sportradar-developer-api-base-url:https://developer.sportradar.com}")
    private String sportradarDeveloperApiBaseUrl;

    // TheOddsAPI configuration
    @Value("${theodds-api-host:api.the-odds-api.com}")
    private String theOddsApiHost;
    @Getter
    @Value("${theodds-api-key:e8f09cbed72bb9cb5fdeda4fd5d27798}")
    private String theOddsApiKey;
    @Value("${theodds-api-base-url:https://api.the-odds-api.com}")
    private String theOddsApiBaseUrl;


    @Bean
    public RestClient rapidApiRestClient() {
        return RestClient.builder()
                .baseUrl(rapidApiBaseUrl)
                .defaultHeader("x-rapidapi-host", rapidApiHost)
                .defaultHeader("x-rapidapi-key", rapidApiKey)
                .build();
    }

    @Bean
    public RestClient sportRadarRestClient() {
        return RestClient.builder()
                .baseUrl(sportradarApiBaseUrl)
                .defaultHeader("x-api-key", sportradarApiKey)
                .build();
    }

    @Bean
    public RestClient developerSportRadarRestClient() { // New RestClient bean
        return RestClient.builder()
                .baseUrl(sportradarDeveloperApiBaseUrl)
                .defaultHeader("x-api-key", sportradarApiKey) // Using the x-api-key header as requested
                .build();
    }

    // Getter methods for accessing config values from services
    public String getRapidApiBaseUrl() {
        return rapidApiBaseUrl;
    }

    public String getSportRadarApiBaseUrl() {
        return sportradarApiBaseUrl;
    }

    public String getDeveloperSportRadarApiBaseUrl() { // New getter
        return sportradarDeveloperApiBaseUrl;
    }

    @Bean
    public RestClient theOddsApiRestClient() {
        return RestClient.builder()
                .baseUrl(theOddsApiBaseUrl)
                .build();
    }

//    public String getTheOddsApiBaseUrl() {
//        return theOddsApiBaseUrl;
//    }
//
//    public String getTheOddsApiHost() {
//        return theOddsApiHost;
//    }
//
//    // getTheOddsApiKey() is already available via Lombok @Getter
}
