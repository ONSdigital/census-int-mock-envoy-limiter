package uk.gov.ons.ctp.integration.mockenvoylimiter.endpoint;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.ons.ctp.common.domain.CaseType;
import uk.gov.ons.ctp.common.domain.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.common.endpoint.CTPEndpoint;
import uk.gov.ons.ctp.integration.common.product.model.Product;
import uk.gov.ons.ctp.integration.common.product.model.Product.DeliveryChannel;
import uk.gov.ons.ctp.integration.mockenvoylimiter.emulator.MockLimiterCaller;
import uk.gov.ons.ctp.integration.mockenvoylimiter.emulator.RateLimiterClientRequest;
import uk.gov.ons.ctp.integration.ratelimiter.model.RateLimitRequest;
import uk.gov.ons.ctp.integration.ratelimiter.model.RateLimitResponse;

/** Endpoints for the mock envoy limiter. */
@RestController
@RequestMapping(value = "/", produces = "application/json")
public final class MockEnvoyLimiterEndpoints implements CTPEndpoint {

  @Autowired private MockLimiterCaller mockLimiterCaller;

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

  private final List<RateLimitRequest> capturedRequests = new ArrayList<>();

  @RequestMapping(value = "/info", method = RequestMethod.GET)
  public ResponseEntity<String> info() {
    return ResponseEntity.ok("CENSUS MOCK ENVOY LIMITER");
  }

  @RequestMapping(value = "/json", method = RequestMethod.POST)
  public ResponseEntity<RateLimitResponse> json(@RequestBody RateLimitRequest rateLimitRequestDTO) {
    // Record request
    capturedRequests.add(rateLimitRequestDTO);

    RateLimiterClientRequest request = new RateLimiterClientRequest();
    Product product = new Product();
    rateLimitRequestDTO
        .getDescriptors()
        .forEach(
            desc ->
                desc.getEntries()
                    .forEach(
                        entry -> {
                          final String key = entry.getKey();
                          final String value = entry.getValue();
                          if (key.equals("productGroup")) {
                            product.setProductGroup(Product.ProductGroup.valueOf(value));
                          }
                          if (key.equals("individual")) {
                            product.setIndividual(Boolean.valueOf(value));
                          }
                          if (key.equals("deliveryChannel")) {
                            product.setDeliveryChannel(DeliveryChannel.valueOf(value));
                          }
                          if (key.equals("caseType")) {
                            request.setCaseType(CaseType.valueOf(value));
                          }
                          if (key.equals("ipAddress")) {
                            request.setIpAddress(value);
                          }
                          if (key.equals("uprn")) {
                            request.setUprn(UniquePropertyReferenceNumber.create(value));
                          }
                          if (key.equals("telNo")) {
                            request.setTelNo(value);
                          }
                        }));
    request.setProduct(product);

    RateLimitResponse response =
        mockLimiterCaller.checkRateLimit(
            request.getProduct(),
            request.getCaseType(),
            request.getIpAddress(),
            request.getUprn(),
            request.getTelNo());

    HttpStatus httpStatus =
        response.getOverallCode().equals("OK") ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;
    return ResponseEntity.status(httpStatus).body(response);
  }

  @RequestMapping(value = "/limit", method = RequestMethod.POST)
  public ResponseEntity<String> limit(@RequestParam("enabled") boolean enabled) {
    // Reset recorded data
    capturedRequests.clear();

    // Update limit type caller has told us to use
    LimitMode limitMode = enabled ? LimitMode.LimitEnabled : LimitMode.NoLimits;

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
