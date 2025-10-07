package piven.example.camunda7.tasks;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Slf4j
@Component("documentService")
public class DocumentProcessingService {

    public void processDocument(DelegateExecution execution) {
        String documentId = (String) execution.getVariable("documentId");

        log.info("Обрабатываем документ " + documentId);
    }
}
