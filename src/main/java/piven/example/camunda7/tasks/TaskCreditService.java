package piven.example.camunda7.tasks;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;

@Service("taskCredit")
public class TaskCreditService {
    public Integer getScoring(DelegateExecution execution) {
        var isNewClient = (Boolean) execution.getVariable("isNewClient");
        var income = (Integer) execution.getVariable("income");
        var age = (Integer) execution.getVariable("age");

        // Упрощенная логика скоринга (возвращаем числовой score)
        int score = 0;

        if (Boolean.TRUE.equals(isNewClient) && income != null && income > 30000 && age != null && age > 21) {
            score = 80; // Высокий score
        } else if (income != null && income > 20000) {
            score = 60; // Средний score
        } else if (income != null && income > 10000) {
            score = 40; // Низкий score
        } else {
            score = 20; // Очень низкий
            if (income == null) {
                execution.setVariable("income", 0);
            }
        }
        execution.setVariable("scoring", score);
        return score;
    }

    public boolean getBlackList(DelegateExecution execution) {
        var clientId = (String) execution.getVariable("clientId");

        try {
            var restTemplate = new RestTemplate();

            var url = UriComponentsBuilder
                    .fromHttpUrl("http://localhost:9090/blacklist")
                    .queryParam("clientId", clientId)
                    .toUriString();

            var response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // сохраняем детали в процесс
                List<String> blackList = Arrays.asList("12345", "99999", "ABC001");
                boolean isBlackListed = blackList.contains(clientId);
                execution.setVariable("isBlackListed", isBlackListed);
                execution.setVariable("blackList", isBlackListed);

                return isBlackListed;
            } else {
                throw new BpmnError("BLACKLIST_CHECK_ERROR",
                        "API вернул код " + response.getStatusCodeValue());
            }

        } catch (Exception e) {
            throw new BpmnError("BLACKLIST_CHECK_ERROR",
                    "Ошибка вызова API ЧС: " + e.getMessage());
        }
    }
}