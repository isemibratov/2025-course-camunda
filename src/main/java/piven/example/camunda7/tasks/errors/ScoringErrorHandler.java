package piven.example.camunda7.tasks.errors;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("scoringErrorHandler")
public class ScoringErrorHandler implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        String clientId = (String) execution.getVariable("clientId");
        log.error("Обработка ошибки скорринга для клиента {}", clientId);
    }
}