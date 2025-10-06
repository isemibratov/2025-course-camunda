package piven.example.camunda7;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import org.camunda.bpm.engine.DecisionService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
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

@SpringBootTest
@DirtiesContext
@Import(ProcessEngineCoverageConfiguration.class)
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

    @Test
    @Deployment(resources = {"bpmn/loanApplicationProcess.bpmn"})
    void testNewClientApproved() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("clientId", "77777");
        vars.put("isNewClient", true);
        vars.put("income", 50000);
        vars.put("age", 30);

        ProcessInstance pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess", vars);
        assertNotNull(pi);

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        completeTask(pi.getId(), "Task_UploadDocuments");

        runtimeService.messageEventReceived("Документы получены",
                runtimeService.createExecutionQuery()
                        .processInstanceId(pi.getId())
                        .messageEventSubscriptionName("Документы получены")
                        .singleResult()
                        .getId());

        runtimeService.setVariable(pi.getId(), "scoring", 30);
        runtimeService.setVariable(pi.getId(), "blackList", false);

        waitForProcessEnd(pi.getId());
    }

    @Test
    @Deployment(resources = {"bpmn/loanApplicationProcess.bpmn"})
    void testExistingClientRejected() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("clientId", "12345");
        vars.put("isNewClient", false);
        vars.put("income", 8000);
        vars.put("age", 25);

        ProcessInstance pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess", vars);
        assertNotNull(pi);

        completeTask(pi.getId(), "Task_SubmitLoanApplication");

        runtimeService.setVariable(pi.getId(), "scoring", 70);
        runtimeService.setVariable(pi.getId(), "blackList", true);

        waitForProcessEnd(pi.getId());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    void testExistingClient_BlacklistRejection() {
        given(taskCreditService.getScoring(any())).willReturn(70);
        var pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess",
                withVariables("clientId", "12345", "isNewClient", false));

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        waitForProcessEnd(pi.getId());

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();

        assertEquals("REJECTED_BLACKLIST", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    void testExistingClient_Approval() {
        given(taskCreditService.getScoring(any())).willReturn(80);
        var pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess",
                withVariables(
                        "clientId", "77777",
                        "isNewClient", false,
                        "income", 30000));

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        waitForProcessEnd(pi.getId());

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();

        assertEquals("APPROVED", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    void testExistingClient_ScoringRejection() {
        given(taskCreditService.getScoring(any())).willReturn(30);
        var pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess",
                withVariables("clientId", "88888", "isNewClient", false));

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        waitForProcessEnd(pi.getId());

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();

        assertEquals("REJECTED_SCORING", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    void testNewClient_BlacklistRejection() {
        given(taskCreditService.getScoring(any())).willReturn(70);

        var pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess",
                withVariables("clientId", "12345", "isNewClient", true));

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        completeTask(pi.getId(), "Task_UploadDocuments");

        simulateDocumentsReceived(pi.getId());
        waitForProcessEnd(pi.getId());

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();

        assertEquals("REJECTED_BLACKLIST", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    void testNewClient_Approval() {
        given(taskCreditService.getScoring(any())).willReturn(80);

        var pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess",
                withVariables(
                        "clientId", "77777",
                        "isNewClient", true,
                        "income", 30000));

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        completeTask(pi.getId(), "Task_UploadDocuments");

        simulateDocumentsReceived(pi.getId());
        waitForProcessEnd(pi.getId());

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();

        assertEquals("APPROVED", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    void testNewClient_ScoringRejection() {
        given(taskCreditService.getScoring(any())).willReturn(30);

        var pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess",
                withVariables("clientId", "88888", "isNewClient", true));

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        completeTask(pi.getId(), "Task_UploadDocuments");

        simulateDocumentsReceived(pi.getId());
        waitForProcessEnd(pi.getId());

        var result = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi.getId())
                .variableName("approvalResult")
                .singleResult();

        assertEquals("REJECTED_SCORING", result.getValue());
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    void testNewClient_DocumentTimeout() {
        given(taskCreditService.getScoring(any())).willReturn(80);
        var pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess",
                withVariables("clientId", "99999", "isNewClient", true));

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        completeTask(pi.getId(), "Task_UploadDocuments");

        var timerJob = managementService.createJobQuery()
                .processInstanceId(pi.getId())
                .timers()
                .singleResult();

        assertNotNull(timerJob, "Timer job should exist");

        managementService.executeJob(timerJob.getId());

        waitForProcessEnd(pi.getId());

        var process = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();

        assertNotNull(process);
        assertEquals("COMPLETED", process.getState());

        var timeoutEvent = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(pi.getId())
                .activityId("Event_ThreeDays")
                .singleResult();

        assertNotNull(timeoutEvent, "Process should go through timeout event");

        var rejectionEvent = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(pi.getId())
                .activityId("Event_NotifyRejection")
                .singleResult();

        assertNotNull(rejectionEvent, "Process should notify about rejection");
    }

    @Test
    @Deployment(resources = {
            "bpmn/loanApplicationProcess.bpmn",
            "dmn/loanApprovalDecision.dmn"
    })
    void testIncomeRejection() {
        given(taskCreditService.getScoring(any())).willReturn(70);
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("loanApplicationProcess",
                Variables.createVariables()
                        .putValue("isNewClient", false)
                        .putValue("clientId", "11111")
                        .putValue("income", 13000));

        completeTask(pi.getId(), "Task_SubmitLoanApplication");
        waitForProcessEnd(pi.getId());

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
        runtimeService.createMessageCorrelation("Документы получены")
                .processInstanceId(processInstanceId)
                .correlate();
    }

    private void waitForProcessEnd(String processInstanceId) {
        await().atMost(15, SECONDS).until(() ->
                runtimeService.createProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .singleResult() == null
        );
    }
}