package piven.example.camunda7.tasks.errors;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("blacklistErrorHandler")
@Slf4j
public class BlacklistErrorHandler implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        String clientId = (String) execution.getVariable("clientId");
        String errorCode = (String) execution.getVariable("loanErrorCode");
        String errorMessage = (String) execution.getVariable("loanErrorMessage");
        log.error("Обработка ошибки blacklist для клиента {}: {} - {}", clientId, errorCode, errorMessage);
        execution.setVariable("blackList", true);
        execution.setVariable("loanErrorHandled", true);
        execution.removeVariable("loanErrorCode");
        execution.removeVariable("loanErrorMessage");
    }
}