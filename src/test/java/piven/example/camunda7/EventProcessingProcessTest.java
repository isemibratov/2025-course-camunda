package piven.example.camunda7;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.assertj.core.api.Assertions;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.community.process_test_coverage.spring_test.platform7.ProcessEngineCoverageConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import piven.example.camunda7.dto.ServiceRequest;
import piven.example.camunda7.tasks.PrepareService;
import piven.example.camunda7.tasks.PublicationService;
import piven.example.camunda7.tasks.SaveService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
@Import(ProcessEngineCoverageConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EventProcessingProcessTest {

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ManagementService managementService;

    @MockBean
    private PrepareService prepareService;

    @MockBean
    private PublicationService publicationService;

    @MockBean
    private SaveService saveService;

    private ProcessInstance processInstance;

    @Test
    void testPositiveScenario() {
        Map<String, Object> variables = createCommonVariables();
        variables.put("scenario", "POSITIVE");
        initializePositiveVariables(variables);

        processInstance = runtimeService.startProcessInstanceByKey(
                "eventProcessing",
                variables
        );

        await().atMost(15, SECONDS).until(() ->
                runtimeService.createProcessInstanceQuery()
                        .processInstanceId(processInstance.getId())
                        .singleResult() == null
        );

        assertThat(processInstance).isNotNull();
        assertThat(processInstance).isEnded();

        verify(prepareService, times(2)).prepareResponse(any(ServiceRequest.class));
        verify(publicationService, times(2)).publish(any(ServiceRequest.class));

        List<HistoricActivityInstance> activities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstance.getId())
                .activityType("callActivity")
                .list();

        List<String> activityIds = activities.stream()
                .map(HistoricActivityInstance::getActivityId)
                .toList();

        Assertions.assertThat(activityIds)
                .contains("Call_ProcessResultPos")
                .doesNotContain("Call_ProcessResultNeg");
    }

    @Test
    void testNegativeScenario() {
        Map<String, Object> variables = createCommonVariables();
        variables.put("scenario", "NEGATIVE");
        initializeNegativeVariables(variables);

        processInstance = runtimeService.startProcessInstanceByKey(
                "eventProcessing",
                variables
        );

        await().atMost(10, SECONDS).until(() ->
                runtimeService.createProcessInstanceQuery()
                        .processInstanceId(processInstance.getId())
                        .singleResult() == null
        );

        assertThat(processInstance).isNotNull();
        assertThat(processInstance).isEnded();

        verify(prepareService, times(2)).prepareResponse(any(ServiceRequest.class));
        verify(publicationService, times(2)).publish(any(ServiceRequest.class));

        var activities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstance.getId())
                .activityType("callActivity")
                .list();

        var activityIds = activities.stream()
                .map(HistoricActivityInstance::getActivityId)
                .toList();

        Assertions.assertThat(activityIds)
                .contains("Call_ProcessResultNeg")
                .doesNotContain("Call_ProcessResultPos");
    }

    @Test
    void testNaturalScenario() {
        var variables = createCommonVariables();
        variables.put("scenario", "NATURAL");
        initializePositiveVariables(variables);
        initializeNegativeVariables(variables);

        processInstance = runtimeService.startProcessInstanceByKey(
                "eventProcessing",
                variables
        );

        await().atMost(10, SECONDS).until(() ->
                runtimeService.createProcessInstanceQuery()
                        .processInstanceId(processInstance.getId())
                        .singleResult() == null
        );

        assertThat(processInstance).isNotNull();
        assertThat(processInstance).isEnded();

        verify(prepareService, times(4)).prepareResponse(any(ServiceRequest.class));
        verify(publicationService, times(4)).publish(any(ServiceRequest.class));

        var activities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstance.getId())
                .activityType("callActivity")
                .list();

        var activityIds = activities.stream()
                .map(HistoricActivityInstance::getActivityId)
                .toList();

        Assertions.assertThat(activityIds)
                .contains("Call_ProcessResultPos");
    }

    @Test
    void testDefaultScenario() {
        var variables = createCommonVariables();
        variables.put("scenario", "UNKNOWN");
        initializePositiveVariables(variables);
        initializeNegativeVariables(variables);

        processInstance = runtimeService.startProcessInstanceByKey(
                "eventProcessing",
                variables
        );

        executeAllJobs();

        assertThat(processInstance).isNotNull();
        assertThat(processInstance).isEnded();

        verify(prepareService, never()).prepareResponse(any(ServiceRequest.class));
        verify(publicationService, never()).publish(any(ServiceRequest.class));
        verify(saveService, never()).save(any(ServiceRequest.class));
    }

    @Test
    void testEmptyScenario() {
        var variables = createCommonVariables();
        variables.put("scenario", "");
        initializePositiveVariables(variables);
        initializeNegativeVariables(variables);

        processInstance = runtimeService.startProcessInstanceByKey(
                "eventProcessing",
                variables
        );

        executeAllJobs();

        assertThat(processInstance).isNotNull();
        assertThat(processInstance).isEnded();

        verify(prepareService, never()).prepareResponse(any(ServiceRequest.class));
        verify(publicationService, never()).publish(any(ServiceRequest.class));
        verify(saveService, never()).save(any(ServiceRequest.class));
    }

    @Test
    void testNullScenario() {
        var variables = createCommonVariables();
        variables.put("scenario", null);
        initializePositiveVariables(variables);
        initializeNegativeVariables(variables);

        processInstance = runtimeService.startProcessInstanceByKey(
                "eventProcessing",
                variables
        );

        executeAllJobs();

        assertThat(processInstance).isNotNull();
        assertThat(processInstance).isEnded();

        verify(prepareService, never()).prepareResponse(any(ServiceRequest.class));
        verify(publicationService, never()).publish(any(ServiceRequest.class));
        verify(saveService, never()).save(any(ServiceRequest.class));
    }

    @Test
    void testGatewayRouting_Positive() {
        testGatewayRouting("POSITIVE",
                List.of("Call_ProcessResultPos"),
                List.of("Call_ProcessResultNeg")
        );
    }

    @Test
    void testGatewayRouting_Negative() {
        testGatewayRouting("NEGATIVE",
                List.of("Call_ProcessResultNeg"),
                List.of("Call_ProcessResultPos")
        );
    }

    @Test
    void testGatewayRouting_Natural() {
        testGatewayRouting("NATURAL",
                List.of("Call_ProcessResultPos"),
                List.of()
        );
    }

    private void testGatewayRouting(String scenario, List<String> expectedActivities, List<String> notExpectedActivities) {
        var variables = createCommonVariables();
        variables.put("scenario", scenario);
        initializePositiveVariables(variables);
        initializeNegativeVariables(variables);

        ProcessInstance pi = runtimeService.startProcessInstanceByKey("eventProcessing", variables);
        executeAllJobs();

        var activities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(pi.getId())
                .activityType("callActivity")
                .list();

        var actualActivities = activities.stream()
                .map(HistoricActivityInstance::getActivityId)
                .toList();

        Assertions.assertThat(actualActivities).containsAll(expectedActivities);
        if (!notExpectedActivities.isEmpty()) {
            Assertions.assertThat(actualActivities).doesNotContainAnyElementsOf(notExpectedActivities);
        }
    }

    @Test
    void testAsyncExecution() {
        var variables = createCommonVariables();
        variables.put("scenario", "POSITIVE");
        initializePositiveVariables(variables);

        ProcessInstance pi = runtimeService.startProcessInstanceByKey("eventProcessing", variables);

        while (true) {
            List<Job> jobs = managementService.createJobQuery().list();
            if (jobs.isEmpty()) {break;}
            Assertions.assertThat(jobs).isNotEmpty();

            for (Job job : jobs) {
                try {
                    managementService.executeJob(job.getId());
                } catch (Exception e) {
                    // Игнорируем ошибки выполнения jobs для тестов
                }
            }
        }

        await().atMost(10, SECONDS).until(() ->
                runtimeService.createProcessInstanceQuery()
                        .processInstanceId(pi.getId())
                        .singleResult() == null
        );

        ProcessInstance endedProcess = runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
        Assertions.assertThat(endedProcess).isNull();
    }

    @Test
    void testProcessVariablesPersistence() {
        var variables = createCommonVariables();
        variables.put("scenario", "POSITIVE");
        initializePositiveVariables(variables);

        processInstance = runtimeService.startProcessInstanceByKey("eventProcessing", variables);
        var processInstanceId = processInstance.getId();

        var persistedVariables = runtimeService.getVariables(processInstanceId);
        Assertions.assertThat(persistedVariables)
                .containsKey("scenario")
                .containsKey("requestIdAliasStartPositivePrepare")
                .containsKey("startPrepareTypePositive");

        Assertions.assertThat(persistedVariables.get("scenario")).isEqualTo("POSITIVE");
    }

    private Map<String, Object> createCommonVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("businessKey", "test-business-key-" + System.currentTimeMillis());
        return variables;
    }

    private void initializePositiveVariables(Map<String, Object> variables) {
        variables.put("enabled", true);
        variables.put("requestIdAliasStartPositivePrepare", "req-prep-pos");
        variables.put("responseAliasStartPositivePrepare", "resp-prep-pos");
        variables.put("startPrepareTypePositive", "start-prep-pos");
        variables.put("requestIdAliasStartPositivePublication", "req-pub-pos");
        variables.put("responseAliasStartPositivePublication", "resp-pub-pos");
        variables.put("startPublicationTypePositive", "start-pub-pos");
        variables.put("requestIdAliasSavePositive", "req-save-pos");
        variables.put("responseAliasSavePositive", "resp-save-pos");
        variables.put("saveTypePositive", "save-pos");
        variables.put("requestIdAliasEndPositivePrepare", "req-end-prep-pos");
        variables.put("responseAliasEndPositivePrepare", "resp-end-prep-pos");
        variables.put("endPrepareTypePositive", "end-prep-pos");
        variables.put("requestIdAliasEndPositivePublication", "req-end-pub-pos");
        variables.put("responseAliasEndPositivePublication", "resp-end-pub-pos");
        variables.put("endPublicationTypePositive", "end-pub-pos");
    }

    private void initializeNegativeVariables(Map<String, Object> variables) {
        variables.put("enabled", true);
        variables.put("requestIdAliasStartNegativePrepare", "req-prep-neg");
        variables.put("responseAliasStartNegativePrepare", "resp-prep-neg");
        variables.put("startPrepareTypeNegative", "start-prep-neg");
        variables.put("requestIdAliasStartNegativePublication", "req-pub-neg");
        variables.put("responseAliasStartNegativePublication", "resp-pub-neg");
        variables.put("startPublicationTypeNegative", "start-pub-neg");
        variables.put("requestIdAliasSaveNegative", "req-save-neg");
        variables.put("responseAliasSaveNegative", "resp-save-neg");
        variables.put("saveTypeNegative", "save-neg");
        variables.put("requestIdAliasEndNegativePrepare", "req-end-prep-neg");
        variables.put("responseAliasEndNegativePrepare", "resp-end-prep-neg");
        variables.put("endPrepareTypeNegative", "end-prep-neg");
        variables.put("requestIdAliasEndNegativePublication", "req-end-pub-neg");
        variables.put("responseAliasEndNegativePublication", "resp-end-pub-neg");
        variables.put("endPublicationTypeNegative", "end-pub-neg");
    }

    private void executeAllJobs() {
        while (true) {
            List<Job> jobs = managementService.createJobQuery().list();
            if (jobs.isEmpty()) {break;}

            for (Job job : jobs) {
                try {
                    managementService.executeJob(job.getId());
                } catch (Exception e) {
                    // Игнорируем ошибки выполнения jobs для тестов
                }
            }
        }
    }

    @AfterEach
    void cleanup() {
        executeAllJobs();

        runtimeService.createProcessInstanceQuery().list()
                .forEach(pi -> {
                    try {
                        runtimeService.deleteProcessInstance(pi.getId(), "Test cleanup");
                    } catch (Exception ignored) {}
                });

        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(hpi -> {
                    try {
                        historyService.deleteHistoricProcessInstance(hpi.getId());
                    } catch (Exception ignored) {}
                });
    }
}