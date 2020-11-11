package uk.gov.ons.ctp.integration.mockenvoylimiter.emulator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ctp.common.domain.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.integration.ratelimiter.model.CurrentLimit;
import uk.gov.ons.ctp.integration.ratelimiter.model.LimitStatus;

/**
 * simulates the real envoy limiter - will store details
 * as each test is run, (is NOT recreated for each test). Normally this would be undesirable in a
 * test situation, but in our case it more realistically models the actual envoy limiter, since we
 * won't be resetting it because it changes state for each test run, then care must be taken
 * creating tests that do not use data that has been used before the in-memory data structures will
 * grow big very fast, so care must be taken if a LOT of calls are made
 */
public class MockLimiter {

  private final Map<String, Integer> allowanceMap = new HashMap<>();
  private final Map<String, Map<String, List<Integer>>> postingsTimeMap = new HashMap<>();
  private final List<UniquePropertyReferenceNumber> blackListedUprnList =
      Collections.singletonList(UniquePropertyReferenceNumber.create("9999999999999"));
  private final List<String> blackListedIpAddressList =
      Collections.singletonList("blacklisted-ipAddress");
  private final List<String> blackListedTelNoList = Collections.singletonList("blacklisted-telNo");

  public MockLimiter() {
    setupAllowances();
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

      if (blackListedIpAddressList.contains(request.getIpAddress())
          || blackListedUprnList.contains(request.getUprn())
          || blackListedTelNoList.contains(request.getTelNo())) {
        numberRequestsAllowed = 0;
        isBlackListed = true;
        requestValidationStatus.setValid(false);
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

    int limitRemaining;
    if (postsWithinScopeCount >= numberRequestsAllowed) {
      requestValidationStatus.setValid(false);
      limitRemaining = 0;
    } else {
      limitRemaining = numberRequestsAllowed - postsWithinScopeCount;
    }

    final CurrentLimit currentLimit = new CurrentLimit(numberRequestsAllowed,"HOUR");
    final LimitStatus limitStatus = new LimitStatus(recordKey, currentLimit, limitRemaining);
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

  private void setupAllowances() {
    allowanceMap.put("SMS-UAC-FALSE-HH-UPRN", 5);
    allowanceMap.put("SMS-UAC-FALSE-HH-TELNO", 10);

    allowanceMap.put("SMS-UAC-FALSE-SPG-UPRN", 5);
    allowanceMap.put("SMS-UAC-FALSE-SPG-TELNO", 10);

    allowanceMap.put("SMS-UAC-FALSE-CE-UPRN", 5);
    allowanceMap.put("SMS-UAC-FALSE-CE-TELNO", 5);

    allowanceMap.put("SMS-UAC-TRUE-HH-UPRN", 10);
    allowanceMap.put("SMS-UAC-TRUE-HH-TELNO", 10);

    allowanceMap.put("SMS-UAC-TRUE-SPG-UPRN", 10);
    allowanceMap.put("SMS-UAC-TRUE-SPG-TELNO", 10);

    allowanceMap.put("SMS-UAC-TRUE-CE-UPRN", 50);
    allowanceMap.put("SMS-UAC-TRUE-CE-TELNO", 50);

    allowanceMap.put("SMS-IP", 100);

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

    allowanceMap.put("POST-CONTINUATION-FALSE-HH-UPRN", 12);
    allowanceMap.put("POST-CONTINUATION-FALSE-SPG-UPRN", 12);

    allowanceMap.put("POST-IP", 50);

    setupTimeMaps();
  }

  private void setupTimeMaps() {
    allowanceMap.forEach(
        (key, value) -> postingsTimeMap.put(key, getNewTimeMap()));
  }

  private Map<String, List<Integer>> getNewTimeMap() {
    return new HashMap<>();
  }

  public void waitHours(final int hours) {
    postingsTimeMap.forEach(
        (key1, value1) ->
            value1.forEach(
                (key2, value2) -> {
                  final List<Integer> updatedTimeList = new ArrayList<>();
                  value2.forEach(
                      timeValue -> {
                        updatedTimeList.add(
                            timeValue
                                - hours); // we only store the day and hour now, so to roll back
                        // just subtract hours
                      });
                  value1.put(key2, updatedTimeList);
                }));
  }
}
