package uk.gov.ons.ctp.integration.mockenvoylimiter.emulator;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.ons.ctp.common.domain.CaseType;
import uk.gov.ons.ctp.common.domain.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.integration.common.product.model.Product;
import uk.gov.ons.ctp.integration.ratelimiter.model.RateLimitResponse;

@Data
@NoArgsConstructor
@Component
public class MockLimiterCaller {

  private MockLimiter mockLimiter = new MockLimiter();

  private static final Logger log = LoggerFactory.getLogger(MockLimiterCaller.class);

  public RateLimitResponse checkRateLimit(
      Product product,
      CaseType caseType,
      String ipAddress,
      UniquePropertyReferenceNumber uprn,
      String telNo) {

    final RateLimiterClientRequest rateLimiterClientRequest =
        new RateLimiterClientRequest(product, caseType, ipAddress, uprn, telNo);
    final RequestValidationStatus requestValidationStatus = mockLimiter.postRequest(rateLimiterClientRequest);

    String overallCode = "OK";
    if (!requestValidationStatus.isValid()) {
      overallCode = "OVER_LIMIT";
    }
    RateLimitResponse response =
        new RateLimitResponse(overallCode, requestValidationStatus.getLimitStatusList());
    final StringBuilder reason = new StringBuilder(overallCode);
    requestValidationStatus
        .getLimitStatusList()
        .forEach(stat -> reason.append(stat.toString()).append(" - "));
    log.info(reason.toString());
    return response;
  }
}
