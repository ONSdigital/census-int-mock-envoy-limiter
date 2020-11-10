package uk.gov.ons.ctp.integration.mockenvoylimiter.endpoint;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.ons.ctp.common.endpoint.CTPEndpoint;
import uk.gov.ons.ctp.integration.ratelimiter.client.RateLimiterClient;
import uk.gov.ons.ctp.integration.ratelimiter.model.CurrentLimit;
import uk.gov.ons.ctp.integration.ratelimiter.model.LimitStatus;
import uk.gov.ons.ctp.integration.ratelimiter.model.RateLimitRequest;
import uk.gov.ons.ctp.integration.ratelimiter.model.RateLimitResponse;

/** Endpoints for the mock envoy limiter. */
@RestController
@RequestMapping(value = "/", produces = "application/json")
public final class MockEnvoyLimiterEndpoints implements CTPEndpoint {
  private static final Logger log = LoggerFactory.getLogger(RateLimiterClient.class);

  private enum LimitMode {
    NoLimits(HttpStatus.OK, "OK"),
    LimitEnabled(HttpStatus.TOO_MANY_REQUESTS, "OVER_LIMIT");

    private LimitMode(HttpStatus httpResponseStatus, String limitStatus) {
      this.httpResponseStatus = httpResponseStatus;
      this.limitStatus = limitStatus;
    }

    HttpStatus httpResponseStatus;
    String limitStatus;
  }

  private LimitMode limitMode = LimitMode.NoLimits;

  private List<RateLimitRequest> capturedRequests = new ArrayList<>();

  @RequestMapping(value = "/info", method = RequestMethod.GET)
  public ResponseEntity<String> info() {
    return ResponseEntity.ok("CENSUS MOCK ENVOY LIMITER");
  }

  @RequestMapping(value = "/json", method = RequestMethod.POST)
  public ResponseEntity<RateLimitResponse> json(@RequestBody RateLimitRequest rateLimitRequestDTO) {
    log.with(rateLimitRequestDTO).info("Limiter request");

    // Record request
    capturedRequests.add(rateLimitRequestDTO);

    // Simulate rate limit response. Either 'OK' or 'OVER_LIMIT'
    List<LimitStatus> statuses = new ArrayList<>();
    for (int i = 0; i < rateLimitRequestDTO.getDescriptors().size(); i++) {
      CurrentLimit currentLimit = new CurrentLimit((i + 1) * 100, "HOUR");
      LimitStatus status = new LimitStatus(limitMode.limitStatus, currentLimit, 999);
      statuses.add(status);
    }

    // Complete building of response
    String httpCode = Integer.toString(limitMode.httpResponseStatus.value());
    RateLimitResponse response = new RateLimitResponse(httpCode, statuses);

    return ResponseEntity.status(limitMode.httpResponseStatus).body(response);
  }

  @RequestMapping(value = "/limit", method = RequestMethod.POST)
  public ResponseEntity<String> limit(@RequestParam("enabled") boolean enabled) {
    // Reset recorded data
    capturedRequests.clear();

    // Update limit type caller has told us to use
    this.limitMode = enabled ? LimitMode.LimitEnabled : LimitMode.NoLimits;

    return ResponseEntity.ok(
        "Limit control called. Now responding with http "
            + limitMode.httpResponseStatus.value()
            + " and code '"
            + limitMode.limitStatus
            + "'");
  }

  @RequestMapping(value = "/requests", method = RequestMethod.GET)
  public ResponseEntity<List<RateLimitRequest>> requests() {
    return ResponseEntity.ok(capturedRequests);
  }
}
