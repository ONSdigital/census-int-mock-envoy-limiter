package uk.gov.ons.ctp.integration.mockenvoylimiter.processor;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import uk.gov.ons.ctp.integration.ratelimiter.model.LimitStatus;

@Data
public class RequestValidationStatus {

  private List<LimitStatus> limitStatusList = new ArrayList<>();
  private boolean valid = true;

  public void add(final LimitStatus limitStatus) {
    limitStatusList.add(limitStatus);
  }
}
