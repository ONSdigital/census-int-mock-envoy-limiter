package uk.gov.ons.ctp.integration.mockenvoylimiter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.integration.caseapiclient.caseservice.model.QuestionnaireIdDTO;

@Configuration
@EnableConfigurationProperties
@PropertySource(factory = YamlPropertySourceFactory.class, value = "classpath:questionnaires.yml")
@ConfigurationProperties("questionnairedata")
public class QuestionnairesConfig {

  private String questionnaires;
  private final Map<String, QuestionnaireIdDTO> questionnaireMap =
      Collections.synchronizedMap(new HashMap<>());

  public String getQuestionnaires() {
    return questionnaires;
  }

  public void setQuestionnaires(String questionnaires) throws IOException, CTPException {
    this.questionnaires = questionnaires;
    final ObjectMapper objectMapper = new ObjectMapper();
    final List<QuestionnaireIdDTO> questionnaireIdDTOList =
        objectMapper.readValue(questionnaires, new TypeReference<List<QuestionnaireIdDTO>>() {});
    addData(questionnaireIdDTOList);
  }

  /**
   * add data in the maps from a list of Questionnaires
   *
   * @param questionnaireIdDTOList - list of questionnaires
   */
  public void addData(final List<QuestionnaireIdDTO> questionnaireIdDTOList) throws CTPException {
    for (QuestionnaireIdDTO q : questionnaireIdDTOList) {
      if (questionnaireMap.containsKey(q.getQuestionnaireId())) {
        throw new CTPException(
            CTPException.Fault.BAD_REQUEST,
            "Duplicate questionnaire ID: " + q.getQuestionnaireId() + " unable to update maps");
      } else {
        updateMaps(q);
      }
    }
  }

  /**
   * Update map from a questionnaire
   *
   * @param questionnaire - a questionnaire
   */
  private synchronized void updateMaps(final QuestionnaireIdDTO questionnaire) {
    synchronized (questionnaireMap) {
      questionnaireMap.put(questionnaire.getQuestionnaireId(), questionnaire);
    }
  }

  /**
   * Reset the data maps back to the original JSON
   *
   * @throws IOException - thrown
   */
  public synchronized void resetData() throws IOException, CTPException {
    synchronized (questionnaireMap) {
      questionnaireMap.clear();
      setQuestionnaires(questionnaires);
    }
  }

  public QuestionnaireIdDTO getQuestionnaire(final String key) {
    return questionnaireMap.getOrDefault(key, null);
  }

  @Override
  public String toString() {
    return "{" + getQuestionnaires() + "}";
  }
}
