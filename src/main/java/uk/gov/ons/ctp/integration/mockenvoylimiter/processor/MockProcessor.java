package uk.gov.ons.ctp.integration.mockenvoylimiter.processor;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    final IsValidRequest isValidRequest = postRequest(rateLimiterClientRequest);

    String overallCode = "OK";
    if (!isValidRequest.isValid()) {
      overallCode = "OVER_LIMIT";
    }
    RateLimitResponse response =
        new RateLimitResponse(overallCode, isValidRequest.getLimitStatusList());
    final StringBuilder reason = new StringBuilder(overallCode);
    isValidRequest
        .getLimitStatusList()
        .forEach(stat -> reason.append(stat.toString()).append(" - "));
    log.info(reason.toString());
    return response;
  }

  public IsValidRequest postRequest(final RateLimiterClientRequest rateLimiterClientRequest)
      throws ResponseStatusException {

    List<String> requestKeyList = getKeys(rateLimiterClientRequest);
    final IsValidRequest isValidRequest =
        isValidateRequest(requestKeyList, rateLimiterClientRequest);
    postRequest(
        requestKeyList,
        rateLimiterClientRequest); // always post - it burns allowances every time for all scenarios

    return isValidRequest;
  }

  private List<String> getKeys(RateLimiterClientRequest request) {
    List<String> keyList = new ArrayList<>();
    StringBuilder keyBuff =
        new StringBuilder(request.getProduct().getProductGroup().name().toUpperCase())
            .append("-")
            .append(request.getProduct().getIndividual().toString().toUpperCase())
            .append("-")
            .append(request.getProduct().getDeliveryChannel().name().toUpperCase())
            .append("-")
            .append(request.getCaseType().name().toUpperCase())
            .append("-");
    if (request.getUprn() != null && request.getUprn().getValue() != 0) {
      keyList.add(keyBuff.toString() + "UPRN");
    }
    if (request.getIpAddress() != null) {
      keyList.add(keyBuff.toString() + "IP");
    }
    if (request.getTelNo() != null) {
      keyList.add(keyBuff.toString() + "TELNO");
    }
    return keyList;
  }

  private IsValidRequest isValidateRequest(
      List<String> requestKeyList, RateLimiterClientRequest f) {
    final IsValidRequest isValidRequest = new IsValidRequest();
    for (String requestKey : requestKeyList) {
      if (!allowanceMap.containsKey(requestKey)) {
        continue; // not in scope so is valid....
      }
      final int noRequestAllowed = allowanceMap.get(requestKey);
      Map<String, List<Integer>> postedMap = postingsTimeMap.get(requestKey);
      if (postedMap == null) {
        return isValidRequest;
      }
      final String[] keyConstituents = requestKey.split("-");
      final String keyType = keyConstituents[keyConstituents.length - 1];

      String valueToRecord = "";
      if (keyType.equals("UPRN")) {
        valueToRecord = f.getUprn().getValue() + "";
      }
      if (keyType.equals("IP")) {
        valueToRecord = f.getIpAddress();
      }
      if (keyType.equals("TELNO")) {
        valueToRecord = f.getTelNo();
      }

      final List<Integer> postingsList =
          postedMap.containsKey(valueToRecord) ? postedMap.get(valueToRecord) : new ArrayList<>();

      final Date now = new Date(System.currentTimeMillis());
      final Date oneHourAgo = DateUtils.addHours(now, -1);
      SimpleDateFormat dmformatter = new SimpleDateFormat("DDDHH");
      int dayHourNow = Integer.parseInt(dmformatter.format(now));
      int postsWithinScopeCount = 0;
      for (int postDateHour : postingsList) {
        if (postDateHour == dayHourNow) {
          postsWithinScopeCount++;
        }
      }
      final String recordKey = requestKey + "(" + valueToRecord + ")";
      recordRequest(isValidRequest, recordKey, noRequestAllowed, postsWithinScopeCount);
    }
    if (!isValidRequest.isValid()) {
      dumpMaps(requestKeyList);
    }
    return isValidRequest;
  }

  private void recordRequest(
      final IsValidRequest isValidRequest,
      final String recordKey,
      final int noRequestAllowed,
      final int postsWithinScopeCount) {
    final CurrentLimit currentLimit = new CurrentLimit(noRequestAllowed, "HOUR");
    final LimitStatus limitStatus = new LimitStatus("", currentLimit, 0);
    limitStatus.setCode(recordKey);
    limitStatus.setCurrentLimit(currentLimit);
    if (postsWithinScopeCount >= noRequestAllowed) {
      isValidRequest.setValid(false);
      limitStatus.setLimitRemaining(0);
      limitStatus.setCode("OVER_LIMIT");
    } else {
      limitStatus.setLimitRemaining(noRequestAllowed - postsWithinScopeCount);
    }
    isValidRequest.getLimitStatusList().add(limitStatus);
  }

  private void postRequest(List<String> requestKeyList, final RateLimiterClientRequest request) {
    for (String requestKey : requestKeyList) {
      Map<String, List<Integer>> postedMap = postingsTimeMap.get(requestKey);
      if (postedMap == null) {
        return;
      }
      final String[] keyConstituents = requestKey.split("-");
      final String keyType = keyConstituents[keyConstituents.length - 1];
      String postingsListKey = null;
      if (keyType.equals("UPRN")) {
        postingsListKey = request.getUprn().getValue() + "";
      }
      if (keyType.equals("IP")) {
        postingsListKey = request.getIpAddress();
      }
      if (keyType.equals("TELNO")) {
        postingsListKey = request.getTelNo();
      }
      postedMap.computeIfAbsent(postingsListKey, k -> new ArrayList<>());
      final Date now = new Date(System.currentTimeMillis());
      SimpleDateFormat dmformatter = new SimpleDateFormat("DDDHH");
      int dayHour = Integer.parseInt(dmformatter.format(now));
      postedMap.get(postingsListKey).add(dayHour);
    }
  }

  @PostConstruct
  private void setupAllowances() {
    allowanceMap.put("UAC-FALSE-SMS-HH-UPRN", 5);
    allowanceMap.put("UAC-FALSE-SMS-SPG-UPRN", 5);
    allowanceMap.put("UAC-FALSE-SMS-CE-UPRN", 5);
    allowanceMap.put("UAC-FALSE-SMS-HH-TELNO", 10);
    allowanceMap.put("UAC-FALSE-SMS-SPG-TELNO", 10);
    allowanceMap.put("UAC-FALSE-SMS-CE-TELNO", 5);
    allowanceMap.put("UAC-FALSE-SMS-HH-IP", 100);
    allowanceMap.put("UAC-FALSE-SMS-SPG-IP", 100);
    allowanceMap.put("UAC-FALSE-SMS-CE-IP", 100);
    allowanceMap.put("UAC-TRUE-SMS-HH-UPRN", 10);
    allowanceMap.put("UAC-TRUE-SMS-SPG-UPRN", 10);
    allowanceMap.put("UAC-TRUE-SMS-CE-UPRN", 50);
    allowanceMap.put("UAC-TRUE-SMS-HH-TELNO", 10);
    allowanceMap.put("UAC-TRUE-SMS-SPG-TELNO", 10);
    allowanceMap.put("UAC-TRUE-SMS-CE-TELNO", 50);
    allowanceMap.put("UAC-TRUE-SMS-HH-IP", 100);
    allowanceMap.put("UAC-TRUE-SMS-SPG-IP", 100);
    allowanceMap.put("UAC-TRUE-SMS-CE-IP", 100);
    allowanceMap.put("UAC-FALSE-POST-HH-UPRN", 1);
    allowanceMap.put("UAC-FALSE-POST-SPG-UPRN", 1);
    allowanceMap.put("UAC-FALSE-POST-CE-UPRN", 1);
    allowanceMap.put("UAC-TRUE-POST-HH-UPRN", 5);
    allowanceMap.put("UAC-TRUE-POST-SPG-UPRN", 5);
    allowanceMap.put("UAC-TRUE-POST-CE-UPRN", 50);
    allowanceMap.put("QUESTIONNAIRE-FALSE-POST-HH-UPRN", 1);
    allowanceMap.put("QUESTIONNAIRE-FALSE-POST-SPG-UPRN", 1);
    allowanceMap.put("QUESTIONNAIRE-TRUE-POST-HH-UPRN", 5);
    allowanceMap.put("QUESTIONNAIRE-TRUE-POST-SPG-UPRN", 5);
    allowanceMap.put("QUESTIONNAIRE-TRUE-POST-CE-UPRN", 50);
    allowanceMap.put("CONTINUATION-FALSE-POST-HH-UPRN", 12);
    allowanceMap.put("CONTINUATION-FALSE-POST-SPG-UPRN", 12);
  }

  @PostConstruct
  private void setupTimeMaps() {
    postingsTimeMap.put("UAC-FALSE-SMS-HH-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-SMS-SPG-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-SMS-CE-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-SMS-HH-TELNO", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-SMS-SPG-TELNO", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-SMS-CE-TELNO", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-SMS-HH-IP", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-SMS-SPG-IP", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-SMS-CE-IP", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-SMS-HH-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-SMS-SPG-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-SMS-CE-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-SMS-HH-TELNO", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-SMS-SPG-TELNO", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-SMS-CE-TELNO", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-SMS-HH-IP", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-SMS-SPG-IP", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-SMS-CE-IP", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-POST-HH-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-POST-SPG-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-FALSE-POST-CE-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-POST-HH-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-POST-SPG-UPRN", getNewTimeMap());
    postingsTimeMap.put("UAC-TRUE-POST-CE-UPRN", getNewTimeMap());
    postingsTimeMap.put("QUESTIONNAIRE-FALSE-POST-HH-UPRN", getNewTimeMap());
    postingsTimeMap.put("QUESTIONNAIRE-FALSE-POST-SPG-UPRN", getNewTimeMap());
    postingsTimeMap.put("QUESTIONNAIRE-TRUE-POST-HH-UPRN", getNewTimeMap());
    postingsTimeMap.put("QUESTIONNAIRE-TRUE-POST-SPG-UPRN", getNewTimeMap());
    postingsTimeMap.put("QUESTIONNAIRE-TRUE-POST-CE-UPRN", getNewTimeMap());
    postingsTimeMap.put("CONTINUATION-FALSE-POST-HH-UPRN", getNewTimeMap());
    postingsTimeMap.put("CONTINUATION-FALSE-POST-SPG-UPRN", getNewTimeMap());
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
                        // just subtract 1
                      });
                  value1.put(key2, updatedTimeList);
                }));
  }

  private void dumpMaps(List<String> requestKeyList) {
    postingsTimeMap.forEach(
        (key1, value1) -> {
          if (requestKeyList.contains(key1)) {
            value1.forEach((key2, value2) -> log.info(key1 + ": " + key2 + " = " + value2.size()));
          }
        });
  }
}
