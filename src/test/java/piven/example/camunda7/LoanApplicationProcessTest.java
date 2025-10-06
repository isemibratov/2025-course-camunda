package piven.example.camunda7;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.util.AssertionErrors.fail;

import lombok.SneakyThrows;
import org.camunda.bpm.engine.DecisionService;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.OptimisticLockingException;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.assertions.ProcessEngineTests;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.community.process_test_coverage.spring_test.platform7.ProcessEngineCoverageConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import piven.example.camunda7.tasks.TaskCreditService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
@Import(ProcessEngineCoverageConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class LoanApplicationProcessTest {

    @Autowired
    private DecisionService decisionService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ManagementService managementService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @MockBean
    private TaskCreditService taskCreditService;

    @Autowired
    private ExternalTaskService externalTaskService;

    private static final long TIMEOUT = 5000;

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "bpmn/creditScoringProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    @SneakyThrows
    void testNewClientApproved() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("clientId", "77777");
        vars.put("isNewClient", true);
        vars.put("income", 50000);
        vars.put("age", 30);

        ProcessInstance pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess", vars);

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        completeTask(pi.getId(), "Task_UploadDocuments");
        simulateDocumentsReceived(pi.getId());


        var subPi = waitForSubProcess(pi.getId(), TIMEOUT);
        var task = waitForExternalTask(subPi, "rest", TIMEOUT);
        externalTaskService.complete(task.getId(), "testWorker", Map.of("scoring", 70));
        runtimeService.setVariable(pi.getId(), "blackList", false);
        waitForProcessEnd(pi, 5000);

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();
        assertNotNull(result, "approvalResult не создан!");
        assertEquals("APPROVED", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "bpmn/creditScoringProcess.bpmn"
    })
    void testExistingClientRejected() throws InterruptedException {
        Map<String, Object> vars = Map.of(
                "clientId", "12345",
                "isNewClient", false,
                "income", 8000,
                "age", 25
        );

        var pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess", vars);
        completeTask(pi.getId(), "Task_SubmitLoanApplication");

        var subPi = waitForSubProcess(pi.getId(), TIMEOUT);
        var task = waitForExternalTask(subPi, "rest", TIMEOUT);

        externalTaskService.complete(task.getId(), "testWorker", Map.of("scoring", 70));
        runtimeService.setVariable(pi.getId(), "blackList", true);

        waitForProcessEnd(pi, TIMEOUT);
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "bpmn/creditScoringProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    void testExistingClient_BlacklistRejection() throws InterruptedException {
        var pi = runtimeService.startProcessInstanceByKey(
                "loanApplicationProcess",
                withVariables("clientId", "12345", "isNewClient", false)
        );
        completeTask(pi.getId(), "Task_SubmitLoanApplication");

        var subPi = waitForSubProcess(pi.getId(), TIMEOUT);
        var task = waitForExternalTask(subPi, "rest", TIMEOUT);
        externalTaskService.complete(task.getId(), "testWorker", Map.of("scoring", 70));

        runtimeService.setVariable(pi.getId(), "blackList", true);
        waitForProcessEnd(pi, TIMEOUT);

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();
        assertEquals("REJECTED_BLACKLIST", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "bpmn/creditScoringProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    @SneakyThrows
    void testExistingClient_ScoringRejection() {
        var pi = runtimeService.startProcessInstanceByKey(
                "loanApplicationProcess",
                withVariables("clientId", "88888", "isNewClient", false)
        );

        completeTask(pi.getId(), "Task_SubmitLoanApplication");

        var subPi = waitForSubProcess(pi.getId(), TIMEOUT);
        var task = waitForExternalTask(subPi, "rest", TIMEOUT);
        externalTaskService.complete(task.getId(), "testWorker", Map.of("scoring", 30));
        waitForProcessEnd(pi, TIMEOUT);

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();
        assertEquals("REJECTED_SCORING", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "bpmn/creditScoringProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    @SneakyThrows
    void testNewClient_BlacklistRejection() {
        var pi = runtimeService.startProcessInstanceByKey(
                "loanApplicationProcess",
                withVariables("clientId", "12345", "isNewClient", true)
        );

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        completeTask(pi.getId(), "Task_UploadDocuments");
        simulateDocumentsReceived(pi.getId());

        var subPi = waitForSubProcess(pi.getId(), TIMEOUT);
        var task = waitForExternalTask(subPi, "rest", TIMEOUT);
        externalTaskService.complete(task.getId(), "testWorker", Map.of("scoring", 70));

        waitForProcessEnd(pi, TIMEOUT);

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();
        assertEquals("REJECTED_BLACKLIST", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "bpmn/creditScoringProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    @SneakyThrows
    void testNewClient_Approval() {
        var pi = runtimeService.startProcessInstanceByKey(
                "loanApplicationProcess",
                withVariables("clientId", "77777", "isNewClient", true, "income", 30000)
        );

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        completeTask(pi.getId(), "Task_UploadDocuments");
        simulateDocumentsReceived(pi.getId());

        var subPi = waitForSubProcess(pi.getId(), TIMEOUT);
        var task = waitForExternalTask(subPi, "rest", TIMEOUT);
        externalTaskService.complete(task.getId(), "testWorker", Map.of("scoring", 80));


        waitForProcessEnd(pi, TIMEOUT);

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();
        assertEquals("APPROVED", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "bpmn/creditScoringProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    @SneakyThrows
    void testNewClient_ScoringRejection() {
        var pi = runtimeService.startProcessInstanceByKey(
                "loanApplicationProcess",
                withVariables("clientId", "88888", "isNewClient", true)
        );

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        completeTask(pi.getId(), "Task_UploadDocuments");
        simulateDocumentsReceived(pi.getId());

        var subPi = waitForSubProcess(pi.getId(), TIMEOUT);
        var task = waitForExternalTask(subPi, "rest", TIMEOUT);
        externalTaskService.complete(task.getId(), "testWorker", Map.of("scoring", 30));


        waitForProcessEnd(pi, TIMEOUT);

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();
        assertEquals("REJECTED_SCORING", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "bpmn/creditScoringProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    @SneakyThrows
    void testNewClient_DocumentTimeout() {
        var pi = runtimeService.startProcessInstanceByKey(
                "loanApplicationProcess",
                withVariables("clientId", "99999", "isNewClient", true)
        );

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        completeTask(pi.getId(), "Task_UploadDocuments");

        // Выполнение таймера DocumentTimeout
        var timerJob = managementService.createJobQuery()
                .processInstanceId(pi.getId())
                .timers()
                .singleResult();
        assertNotNull(timerJob, "Timer job должен существовать");
        managementService.executeJob(timerJob.getId());

        waitForProcessEnd(pi, TIMEOUT);

        var process = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
        assertNotNull(process);
        assertEquals("COMPLETED", process.getState());

        var timeoutEvent = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(pi.getId())
                .activityId("Event_ThreeDays")
                .singleResult();
        assertNotNull(timeoutEvent, "Процесс должен пройти через timeout event");

        var rejectionEvent = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(pi.getId())
                .activityId("Event_NotifyRejection")
                .singleResult();
        assertNotNull(rejectionEvent, "Процесс должен уведомить о rejection");
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "bpmn/creditScoringProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    @SneakyThrows
    void testIncomeRejection() {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                "loanApplicationProcess",
                Variables.createVariables()
                        .putValue("isNewClient", false)
                        .putValue("clientId", "11111")
                        .putValue("income", 13000)
        );

        completeTask(pi.getId(), "Task_SubmitLoanApplication");

        var subPi = waitForSubProcess(pi.getId(), TIMEOUT);
        var task = waitForExternalTask(subPi, "rest", TIMEOUT);
        externalTaskService.complete(task.getId(), "testWorker", Map.of("scoring", 70));

        waitForProcessEnd(pi, TIMEOUT);

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();
        assertEquals("REJECTED_INCOME", result.getValue());
    }

    private void completeTask(String processInstanceId, String taskDefinitionKey) {
        var task = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
        assertNotNull(task, "Task " + taskDefinitionKey + " must exist");
        taskService.complete(task.getId());
    }

    private void simulateDocumentsReceived(String processInstanceId) {
        var exec = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .messageEventSubscriptionName("Документы получены")
                .singleResult();
        assertNotNull(exec, "Execution для сообщения не найден!");
        runtimeService.messageEventReceived("Документы получены", exec.getId());
    }

    private ProcessInstance waitForSubProcess(String superProcessInstanceId, long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long interval = 100;
        ProcessInstance subPi = null;

        while ((System.currentTimeMillis() - startTime) < timeoutMillis) {
            List<Job> jobs = managementService.createJobQuery()
                    .processInstanceId(superProcessInstanceId)
                    .list();

            for (Job job : jobs) {
                try {
                    managementService.executeJob(job.getId());
                } catch (OptimisticLockingException ignored) {
                    // если другой поток/таск уже выполнил job — не страшно
                }
            }

            subPi = runtimeService.createProcessInstanceQuery()
                    .superProcessInstanceId(superProcessInstanceId)
                    .singleResult();

            if (subPi != null) {break;}

            Thread.sleep(interval);
        }

        assertNotNull(subPi, "Call Activity не создала подпроцесс!");
        return subPi;
    }

    private LockedExternalTask waitForExternalTask(ProcessInstance pi, String topic, long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        List<LockedExternalTask> tasks;

        while ((System.currentTimeMillis() - startTime) < timeoutMillis) {
            tasks = externalTaskService.fetchAndLock(10, "testWorker")
                    .topic(topic, 1000)
                    .execute()
                    .stream()
                    .filter(t -> t.getProcessInstanceId().equals(pi.getId()))
                    .collect(Collectors.toList());

            if (!tasks.isEmpty()) {return tasks.get(0);}
            Thread.sleep(100);
        }

        fail("External Task не найден!");
        return null;
    }

    private void waitForProcessEnd(ProcessInstance pi, long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long interval = 100;

        while ((System.currentTimeMillis() - startTime) < timeoutMillis) {
            List<Job> jobs = managementService.createJobQuery()
                    .processInstanceId(pi.getId())
                    .list();

            for (Job job : jobs) {
                try {
                    managementService.executeJob(job.getId());
                } catch (OptimisticLockingException ignored) {}
            }

            boolean ended = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(pi.getId())
                    .singleResult() == null;

            if (ended) {break;}
            Thread.sleep(interval);
        }

        ProcessEngineTests.assertThat(pi).isEnded();
    }
}