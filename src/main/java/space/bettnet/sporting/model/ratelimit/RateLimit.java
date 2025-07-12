package space.bettnet.sporting.model.ratelimit;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Rate limit configuration for an API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimit {
    private String id;
    private int maxCount;
    private ChronoUnit timeUnit;

    // Use timestamps directly instead of wrapping in UsageRecord objects
    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private List<ZonedDateTime> usageTimestamps = new ArrayList<>();
}
