package uk.gov.ons.ctp.integration.mockenvoylimiter.processor;

import lombok.Data;
import uk.gov.ons.ctp.common.domain.CaseType;
import uk.gov.ons.ctp.common.domain.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.integration.common.product.model.Product;

@Data
public class RateLimiterClientRequest {

  private Product product;
  private CaseType caseType;
  private String ipAddress;
  private UniquePropertyReferenceNumber uprn;
  private String telNo;

  public RateLimiterClientRequest(
      Product product,
      CaseType caseType,
      String ipAddress,
      UniquePropertyReferenceNumber uprn,
      String telNo) {
    this.product = product;
    this.caseType = caseType;
    this.ipAddress = ipAddress;
    this.uprn = uprn;
    this.telNo = telNo;
  }

  public RateLimiterClientRequest() {}

  public String toString() {
    return String.format(product.toString(), caseType, ipAddress, uprn, telNo);
  }
}
