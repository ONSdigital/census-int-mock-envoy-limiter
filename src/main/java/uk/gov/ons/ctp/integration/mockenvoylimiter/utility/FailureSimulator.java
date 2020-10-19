package uk.gov.ons.ctp.integration.mockenvoylimiter.utility;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class FailureSimulator {

  private static final Map<Integer, HttpStatus> faultCodes = new HashMap<>();

  static {
    faultCodes.put(400, HttpStatus.BAD_REQUEST);
    faultCodes.put(401, HttpStatus.UNAUTHORIZED);
    faultCodes.put(404, HttpStatus.NOT_FOUND);
    faultCodes.put(500, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  public static void optionallyTriggerFailure(String key, Integer... failureCodes) {
    for (int failureCode : failureCodes) {
      String failureCodeAsString = Integer.toString(failureCode);
      if (key.startsWith(failureCodeAsString)) {
        throw new ResponseStatusException(faultCodes.get(failureCode));
      }
    }
  }
}
