package uk.gov.ons.ctp.integration.mockenvoylimiter.processor;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ctp.common.domain.CaseType;
import uk.gov.ons.ctp.common.domain.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.integration.common.product.model.Product;
import uk.gov.ons.ctp.integration.ratelimiter.model.CurrentLimit;
import uk.gov.ons.ctp.integration.ratelimiter.model.LimitStatus;
import uk.gov.ons.ctp.integration.ratelimiter.model.RateLimitResponse;

@Data
@NoArgsConstructor
@Component
public class MockProcessor {

  private static final Logger log = LoggerFactory.getLogger(MockProcessor.class);

  private Map<String, Integer> allowanceMap = new HashMap<>();
  private Map<String, Map<String, List<Integer>>> postingsTimeMap = new HashMap<>();

  private List<UniquePropertyReferenceNumber> blackListedUprnList =
      Collections.singletonList(UniquePropertyReferenceNumber.create("666"));
  private List<String> blackListedIpAddressList =
      Collections.singletonList("blacklisted-ipAddress");
  private List<String> blackListedTelNoList = Collections.singletonList("blacklisted-telNo");

  @PostConstruct
  private void clearMaps() {
    allowanceMap.clear();
    postingsTimeMap.clear();
  }

  public RateLimitResponse checkRateLimit(
      Product product,
      CaseType caseType,
      String ipAddress,
      UniquePropertyReferenceNumber uprn,
      String telNo) {

    final RateLimiterClientRequest rateLimiterClientRequest =
        new RateLimiterClientRequest(product, caseType, ipAddress, uprn, telNo);
    final RequestValidationStatus requestValidationStatus = postRequest(rateLimiterClientRequest);

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

  public RequestValidationStatus postRequest(
      final RateLimiterClientRequest rateLimiterClientRequest) throws ResponseStatusException {

    List<String> requestKeyList = getKeys(rateLimiterClientRequest);
    final RequestValidationStatus requestValidationStatus =
        createRequestValidationStatus(requestKeyList, rateLimiterClientRequest);
    postRequest(
        requestKeyList,
        rateLimiterClientRequest); // always post - it burns allowances every time for all scenarios

    return requestValidationStatus;
  }

  private List<String> getKeys(RateLimiterClientRequest request) {
    List<String> keyList = new ArrayList<>();
    if (request.getIpAddress() != null) {
      keyList.add(request.getProduct().getDeliveryChannel().name().toUpperCase() + "-IP");
    }
    StringBuilder keyBuff =
        new StringBuilder()
            .append(request.getProduct().getDeliveryChannel().name().toUpperCase())
            .append("-")
            .append(request.getProduct().getProductGroup().name().toUpperCase())
            .append("-")
            .append(request.getProduct().getIndividual().toString().toUpperCase())
            .append("-")
            .append(request.getCaseType().name().toUpperCase())
            .append("-");
    if (request.getUprn() != null && request.getUprn().getValue() != 0) {
      keyList.add(keyBuff.toString() + "UPRN");
    }
    if (request.getTelNo() != null) {
      keyList.add(keyBuff.toString() + "TELNO");
    }
    return keyList;
  }

  private RequestValidationStatus createRequestValidationStatus(
      final List<String> requestKeyList, final RateLimiterClientRequest request) {
    final RequestValidationStatus requestValidationStatus = new RequestValidationStatus();
    for (String requestKey : requestKeyList) {
      if (!allowanceMap.containsKey(requestKey)) {
        continue; // not in scope so is valid....
      }

      final Map<String, List<Integer>> postedMap = postingsTimeMap.get(requestKey);
      final String[] keyConstituents = requestKey.split("-");
      final String keyType = keyConstituents[keyConstituents.length - 1];

      int numberRequestsAllowed = allowanceMap.get(requestKey);
      boolean isBlackListed = false;
      if (keyType.equals("UPRN") && blackListedUprnList.contains(request.getUprn())) {
        numberRequestsAllowed = 0;
        isBlackListed = true;
      }
      if (keyType.equals("IP") && blackListedIpAddressList.contains(request.getIpAddress())) {
        numberRequestsAllowed = 0;
        isBlackListed = true;
      }
      if (keyType.equals("TELNO") && blackListedTelNoList.contains(request.getTelNo())) {
        numberRequestsAllowed = 0;
        isBlackListed = true;
      }

      if (!isBlackListed && postedMap == null) {
        return requestValidationStatus;
      }

      String keyToRecord = getListKey(request, keyType);
      final List<Integer> postingsList =
          postedMap != null && postedMap.containsKey(keyToRecord)
              ? postedMap.get(keyToRecord)
              : new ArrayList<>();

      final Date now = new Date(System.currentTimeMillis());
      SimpleDateFormat dmformatter = new SimpleDateFormat("DDDHH");
      int dayHourNow = Integer.parseInt(dmformatter.format(now));
      int postsWithinScopeCount = 0;
      for (int postDateHour : postingsList) {
        if (postDateHour == dayHourNow) {
          postsWithinScopeCount++;
        }
      }
      final String recordKey = requestKey + "(" + keyToRecord + ")";
      recordRequest(
          requestValidationStatus, recordKey, numberRequestsAllowed, postsWithinScopeCount);
    }
    return requestValidationStatus;
  }

  private void recordRequest(
      final RequestValidationStatus requestValidationStatus,
      final String recordKey,
      final int numberRequestsAllowed,
      final int postsWithinScopeCount) {
    final CurrentLimit currentLimit = new CurrentLimit(numberRequestsAllowed, "HOUR");
    final LimitStatus limitStatus = new LimitStatus("", currentLimit, 0);
    limitStatus.setCode(recordKey);
    if (postsWithinScopeCount >= numberRequestsAllowed) {
      requestValidationStatus.setValid(false);
      limitStatus.setLimitRemaining(0);
      limitStatus.setCode("OVER_LIMIT");
    } else {
      limitStatus.setLimitRemaining(numberRequestsAllowed - postsWithinScopeCount);
    }
    requestValidationStatus.getLimitStatusList().add(limitStatus);
  }

  private void postRequest(List<String> requestKeyList, final RateLimiterClientRequest request) {
    for (String requestKey : requestKeyList) {
      Map<String, List<Integer>> postedMap = postingsTimeMap.get(requestKey);
      if (postedMap == null) {
        return;
      }
      final String[] keyConstituents = requestKey.split("-");
      final String keyType = keyConstituents[keyConstituents.length - 1];
      final String listKey = getListKey(request, keyType);

      postedMap.computeIfAbsent(listKey, k -> new ArrayList<>());
      final Date now = new Date(System.currentTimeMillis());
      SimpleDateFormat dmformatter = new SimpleDateFormat("DDDHH");
      int dayHour = Integer.parseInt(dmformatter.format(now));
      postedMap.get(listKey).add(dayHour);
    }
  }

  private String getListKey(final RateLimiterClientRequest request, final String keyType) {
    String listKey = null;
    if (keyType.equals("UPRN")) {
      listKey = request.getUprn().getValue() + "";
    }
    if (keyType.equals("IP")) {
      listKey = request.getIpAddress();
    }
    if (keyType.equals("TELNO")) {
      listKey = request.getTelNo();
    }
    return listKey;
  }

  @PostConstruct
  private void setupAllowances() {
    allowanceMap.put("SMS-UAC-FALSE-HH-UPRN", 5);
    allowanceMap.put("SMS-UAC-FALSE-SPG-UPRN", 5);
    allowanceMap.put("SMS-UAC-FALSE-CE-UPRN", 5);
    allowanceMap.put("SMS-UAC-FALSE-HH-TELNO", 10);
    allowanceMap.put("SMS-UAC-FALSE-SPG-TELNO", 10);
    allowanceMap.put("SMS-UAC-FALSE-CE-TELNO", 5);
    allowanceMap.put("SMS-IP", 100);
    allowanceMap.put("SMS-UAC-TRUE-HH-UPRN", 10);
    allowanceMap.put("SMS-UAC-TRUE-SPG-UPRN", 10);
    allowanceMap.put("SMS-UAC-TRUE-CE-UPRN", 50);
    allowanceMap.put("SMS-UAC-TRUE-HH-TELNO", 10);
    allowanceMap.put("SMS-UAC-TRUE-SPG-TELNO", 10);
    allowanceMap.put("SMS-UAC-TRUE-CE-TELNO", 50);
    allowanceMap.put("POST-UAC-FALSE-HH-UPRN", 1);
    allowanceMap.put("POST-UAC-FALSE-SPG-UPRN", 1);
    allowanceMap.put("POST-UAC-FALSE-CE-UPRN", 1);
    allowanceMap.put("POST-UAC-TRUE-HH-UPRN", 5);
    allowanceMap.put("POST-UAC-TRUE-SPG-UPRN", 5);
    allowanceMap.put("POST-UAC-TRUE-CE-UPRN", 50);
    allowanceMap.put("POST-QUESTIONNAIRE-FALSE-HH-UPRN", 1);
    allowanceMap.put("POST-QUESTIONNAIRE-FALSE-SPG-UPRN", 1);
    allowanceMap.put("POST-QUESTIONNAIRE-TRUE-HH-UPRN", 5);
    allowanceMap.put("POST-QUESTIONNAIRE-TRUE-SPG-UPRN", 5);
    allowanceMap.put("POST-QUESTIONNAIRE-TRUE-CE-UPRN", 50);
    allowanceMap.put("POST-LARGE_PRINT-FALSE-HH-UPRN", 1);
    allowanceMap.put("POST-LARGE_PRINT-FALSE-SPG-UPRN", 1);
    allowanceMap.put("POST-LARGE_PRINT-TRUE-HH-UPRN", 5);
    allowanceMap.put("POST-LARGE_PRINT-TRUE-SPG-UPRN", 5);
    allowanceMap.put("POST-LARGE_PRINT-TRUE-CE-UPRN", 50);
    allowanceMap.put("POST-CONTINUATION-TRUE-HH-UPRN", 12);
    allowanceMap.put("POST-CONTINUATION-TRUE-SPG-UPRN", 12);
    allowanceMap.put("POST-IP", 100);
    setupTimeMaps();
  }

  private void setupTimeMaps() {
    allowanceMap.forEach(
        (key, value) -> postingsTimeMap.put(key, getNewTimeMap()));
  }

  private Map<String, List<Integer>> getNewTimeMap() {
    return new HashMap<>();
  }
}
