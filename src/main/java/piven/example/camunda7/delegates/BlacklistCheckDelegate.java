package piven.example.camunda7.delegates;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component("blacklistCheck")
@Slf4j
public class BlacklistCheckDelegate implements JavaDelegate {
    private static final String BLACKLIST_CHECK_ERROR = "BLACKLIST_CHECK_ERROR";

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        var clientId = (String) execution.getVariable("clientId");

        if (Boolean.TRUE.equals(execution.getVariable("forceBlacklistError"))) {
            if (execution.getVariable("errorAlreadyThrown") == null) {
                execution.setVariable("errorAlreadyThrown", true);
                execution.setVariable("loanErrorCode", BLACKLIST_CHECK_ERROR);
                execution.setVariable("loanErrorMessage", "Тестовая ошибка API ЧС");
                throw new BpmnError(BLACKLIST_CHECK_ERROR, "Тестовая ошибка API ЧС");
            } else {
                log.warn("Ошибка уже была выброшена, повторный вызов делегата");
            }
        }

        var blackList = Arrays.asList("12345", "99999", "ABC001");
        if (blackList.contains(clientId)) {
            execution.setVariable("loanErrorCode", BLACKLIST_CHECK_ERROR);
            execution.setVariable("loanErrorMessage", "Клиент найден в ЧС");
            throw new BpmnError(BLACKLIST_CHECK_ERROR, "Клиент в ЧС");
        }

        execution.setVariable("blackList", false);
    }
}