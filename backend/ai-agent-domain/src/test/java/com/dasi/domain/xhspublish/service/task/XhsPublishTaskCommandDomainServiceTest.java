package com.dasi.domain.xhspublish.service.task;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.dto.RetryXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.SubmitXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.valobj.PublishAttemptStatusVO;
import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTaskStatusVO;
import com.dasi.domain.xhspublish.service.domain.IPublishRetryDomainService;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.support.XhsPublishBindingSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishExecutionSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskLockSupport;
import com.dasi.types.exception.WorkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishTaskCommandDomainServiceTest {

    private XhsPublishTaskCommandDomainService service;

    @Mock
    private IXhsPublishRepository publishRepository;
    @Mock
    private XhsPublishBindingSupport bindingSupport;
    @Mock
    private XhsPublishIdSupport idSupport;
    @Mock
    private IPublishValidationDomainService validationDomainService;
    @Mock
    private IPublishRetryDomainService retryDomainService;
    @Mock
    private IPublishTaskStateMachine taskStateMachine;
    @Mock
    private XhsPublishTaskAccessSupport taskAccessSupport;
    @Mock
    private XhsPublishExecutionSupport executionSupport;

    @BeforeEach
    void setUp() {
        service = new XhsPublishTaskCommandDomainService();
        ReflectionTestUtils.setField(service, "publishRepository", publishRepository);
        ReflectionTestUtils.setField(service, "bindingSupport", bindingSupport);
        ReflectionTestUtils.setField(service, "idSupport", idSupport);
        ReflectionTestUtils.setField(service, "validationDomainService", validationDomainService);
        ReflectionTestUtils.setField(service, "retryDomainService", retryDomainService);
        ReflectionTestUtils.setField(service, "taskStateMachine", taskStateMachine);
        ReflectionTestUtils.setField(service, "taskAccessSupport", taskAccessSupport);
        ReflectionTestUtils.setField(service, "executionSupport", executionSupport);
        ReflectionTestUtils.setField(service, "taskLockSupport", new XhsPublishTaskLockSupport());
    }

    @Test
    void shouldRejectSubmitWhenLatestAttemptIsActive() {
        XhsPublishTaskEntity task = task("task-1");
        when(taskAccessSupport.queryOwnedTask("task-1")).thenReturn(task);
        when(bindingSupport.resolveBinding(1L, "binding-1")).thenReturn(binding("binding-1"));
        when(publishRepository.listAttemptByTaskId("task-1")).thenReturn(List.of(attempt("attempt-running", 1, PublishAttemptStatusVO.running.name())));

        SubmitXhsPublishTaskDTO dto = SubmitXhsPublishTaskDTO.builder().taskId("task-1").triggerType("submit").build();
        assertThrows(WorkException.class, () -> service.submitTask(dto));
    }

    @Test
    void shouldCreateAttemptAndExecuteOnSubmit() {
        XhsPublishTaskEntity task = task("task-1");
        XhsPublishAccountBindingEntity binding = binding("binding-1");
        XhsPublishAttemptEntity persistedAttempt = attempt("attempt-1", 1, PublishAttemptStatusVO.created.name());

        when(taskAccessSupport.queryOwnedTask("task-1")).thenReturn(task);
        when(bindingSupport.resolveBinding(1L, "binding-1")).thenReturn(binding);
        when(publishRepository.listAttemptByTaskId("task-1")).thenReturn(List.of());
        when(taskStateMachine.nextTaskStatus(PublishTaskStatusVO.draft.name(), "submit_requested")).thenReturn(PublishTaskStatusVO.running.name());
        when(taskStateMachine.nextStage(PublishStageVO.planning.name(), "submit_requested")).thenReturn(PublishStageVO.planning.name());
        when(idSupport.nextAttemptId()).thenReturn("attempt-1");
        when(publishRepository.queryAttemptByAttemptId("attempt-1")).thenReturn(persistedAttempt);
        when(publishRepository.listAssetByTaskId("task-1")).thenReturn(List.of());
        doNothing().when(publishRepository).saveTask(any());
        doNothing().when(publishRepository).saveAttempt(any());

        SubmitXhsPublishTaskDTO dto = SubmitXhsPublishTaskDTO.builder().taskId("task-1").triggerType("submit").build();
        String attemptId = service.submitTask(dto);

        assertEquals("attempt-1", attemptId);
        verify(publishRepository).saveAttempt(any(XhsPublishAttemptEntity.class));
        verify(executionSupport).execute(eq(task), eq(persistedAttempt), eq(binding), eq(List.of()));
    }

    @Test
    void shouldRejectRetryWhenNoAttemptHistory() {
        XhsPublishTaskAggregate aggregate = XhsPublishTaskAggregate.builder()
                .task(task("task-1"))
                .attemptList(List.of())
                .build();
        when(taskAccessSupport.queryOwnedAggregate("task-1")).thenReturn(aggregate);

        RetryXhsPublishTaskDTO dto = RetryXhsPublishTaskDTO.builder().taskId("task-1").triggerType("manual_retry").build();
        assertThrows(WorkException.class, () -> service.retryTask(dto));
    }

    @Test
    void shouldCreateAttemptAndExecuteOnRetry() {
        XhsPublishTaskEntity task = task("task-1");
        task.setRetryPolicyJson("{\"maxRetry\":3}");
        task.setTaskStatus(PublishTaskStatusVO.failed.name());
        task.setCurrentStage(PublishStageVO.failed_terminal.name());

        XhsPublishAttemptEntity latestAttempt = attempt("attempt-1", 1, PublishAttemptStatusVO.failed.name());
        latestAttempt.setDurationMs(300L);
        latestAttempt.setCostAmount(new BigDecimal("0.6"));

        XhsPublishTaskAggregate aggregate = XhsPublishTaskAggregate.builder()
                .task(task)
                .attemptList(List.of(latestAttempt))
                .build();
        XhsPublishAccountBindingEntity binding = binding("binding-1");
        XhsPublishAttemptEntity persistedAttempt = attempt("attempt-2", 2, PublishAttemptStatusVO.created.name());

        when(taskAccessSupport.queryOwnedAggregate("task-1")).thenReturn(aggregate);
        when(retryDomainService.canRetry(eq(task.getRetryPolicyJson()), eq(1), eq(latestAttempt.getErrorCode()), eq(300L), eq(new BigDecimal("0.6"))))
                .thenReturn(true);
        when(bindingSupport.resolveBinding(1L, "binding-1")).thenReturn(binding);
        when(idSupport.nextAttemptId()).thenReturn("attempt-2");
        when(publishRepository.queryAttemptByAttemptId("attempt-2")).thenReturn(persistedAttempt);
        when(taskStateMachine.nextTaskStatus(PublishTaskStatusVO.failed.name(), "retry_requested")).thenReturn(PublishTaskStatusVO.running.name());
        when(taskStateMachine.nextStage(PublishStageVO.failed_terminal.name(), "start_execute")).thenReturn(PublishStageVO.planning.name());
        when(publishRepository.listAssetByTaskId("task-1")).thenReturn(List.of());
        doNothing().when(publishRepository).saveTask(any());
        doNothing().when(publishRepository).saveAttempt(any());

        RetryXhsPublishTaskDTO dto = RetryXhsPublishTaskDTO.builder().taskId("task-1").triggerType("manual_retry").build();
        service.retryTask(dto);

        verify(retryDomainService).canRetry(eq(task.getRetryPolicyJson()), eq(1), eq(latestAttempt.getErrorCode()), eq(300L), eq(new BigDecimal("0.6")));
        verify(executionSupport).execute(eq(task), eq(persistedAttempt), eq(binding), eq(List.of()));
    }

    private XhsPublishTaskEntity task(String taskId) {
        return XhsPublishTaskEntity.builder()
                .taskId(taskId)
                .userId(1L)
                .publishMode("B")
                .publishType("image")
                .taskStatus(PublishTaskStatusVO.draft.name())
                .currentStage(PublishStageVO.planning.name())
                .bindingId("binding-1")
                .requestJson("{\"title\":\"t\",\"content\":\"c\"}")
                .latestContextJson("{\"title\":\"t\",\"content\":\"c\"}")
                .build();
    }

    private XhsPublishAccountBindingEntity binding(String bindingId) {
        return XhsPublishAccountBindingEntity.builder()
                .bindingId(bindingId)
                .userId(1L)
                .mcpTenantId("tenant-1")
                .mcpAccountId("account-1")
                .bindStatus(1)
                .build();
    }

    private XhsPublishAttemptEntity attempt(String attemptId, int attemptNo, String status) {
        return XhsPublishAttemptEntity.builder()
                .attemptId(attemptId)
                .taskId("task-1")
                .attemptNo(attemptNo)
                .attemptStatus(status)
                .build();
    }

}

