package com.dasi.domain.xhspublish.service.task;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishTemplateRepository;
import com.dasi.domain.xhspublish.model.dto.CreateXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.RetryXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.SubmitXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptStatsEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishReviewEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplateEntity;
import com.dasi.domain.xhspublish.model.valobj.PublishAttemptStatusVO;
import com.dasi.domain.xhspublish.model.valobj.PublishModeVO;
import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTaskStatusVO;
import com.dasi.domain.xhspublish.model.valobj.ReviewStageVO;
import com.dasi.domain.xhspublish.model.valobj.ReviewStatusVO;
import com.dasi.domain.xhspublish.model.dto.ReviewXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.service.domain.IPublishRetryDomainService;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionReasonCode;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import com.dasi.domain.xhspublish.service.support.XhsPublishBindingSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishExecutionSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskLockSupport;
import com.dasi.types.exception.WorkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishTaskCommandDomainServiceTest {

    private XhsPublishTaskCommandDomainService service;

    @Mock
    private IXhsPublishRepository publishRepository;
    @Mock
    private IXhsPublishTemplateRepository templateRepository;
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
        ReflectionTestUtils.setField(service, "templateRepository", templateRepository);
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
    void shouldApplyTemplateConfigWhenCreateRequestIsEmptyJson() {
        when(taskAccessSupport.requireUserId()).thenReturn(1L);
        doNothing().when(taskAccessSupport).validateModeAndType("B", "image");
        when(templateRepository.queryByTemplateId("template-1")).thenReturn(XhsPublishTemplateEntity.builder()
                .templateId("template-1")
                .userId(1L)
                .templateStatus(1)
                .templateConfigJson("{\"title\":\"模板标题\",\"content\":\"模板正文\"}")
                .build());
        when(bindingSupport.resolveBinding(1L, "binding-1")).thenReturn(binding("binding-1"));
        when(idSupport.nextTaskId()).thenReturn("task-100");
        doNothing().when(validationDomainService).validateTaskRequest("image", "{\"title\":\"模板标题\",\"content\":\"模板正文\"}");
        doNothing().when(publishRepository).saveTask(any());

        String taskId = service.createTask(CreateXhsPublishTaskDTO.builder()
                .taskName("任务")
                .publishMode("B")
                .publishType("image")
                .bindingId("binding-1")
                .templateId("template-1")
                .requestJson("{}")
                .build());

        assertEquals("task-100", taskId);
        verify(validationDomainService).validateTaskRequest("image", "{\"title\":\"模板标题\",\"content\":\"模板正文\"}");
    }

    @Test
    void shouldQueueCopyReviewWhenSubmitInAMode() {
        XhsPublishTaskEntity task = task("task-a1");
        task.setPublishMode(PublishModeVO.A.name());
        XhsPublishAccountBindingEntity binding = binding("binding-1");
        XhsPublishAttemptEntity persistedAttempt = attempt("attempt-a1", 1, PublishAttemptStatusVO.created.name());

        when(taskAccessSupport.queryOwnedTask("task-a1")).thenReturn(task);
        when(taskAccessSupport.requireUserId()).thenReturn(1L);
        when(bindingSupport.resolveBinding(1L, "binding-1")).thenReturn(binding);
        when(publishRepository.listAttemptByTaskId("task-a1")).thenReturn(List.of());
        when(taskStateMachine.nextTaskStatus(PublishTaskStatusVO.draft.name(), "submit_requested")).thenReturn(PublishTaskStatusVO.running.name());
        when(taskStateMachine.nextStage(PublishStageVO.planning.name(), "submit_requested")).thenReturn(PublishStageVO.planning.name());
        when(taskStateMachine.nextStage(PublishStageVO.planning.name(), "copy_review_required")).thenReturn(PublishStageVO.copy_review_pending.name());
        when(taskStateMachine.nextAttemptStatus(PublishAttemptStatusVO.created.name(), "copy_review_required")).thenReturn(PublishAttemptStatusVO.waiting_review.name());
        when(idSupport.nextAttemptId()).thenReturn("attempt-a1");
        when(idSupport.nextReviewId()).thenReturn("review-a1");
        when(publishRepository.queryAttemptByAttemptId("attempt-a1")).thenReturn(persistedAttempt);
        doNothing().when(publishRepository).saveTask(any());
        doNothing().when(publishRepository).saveAttempt(any());
        doNothing().when(publishRepository).saveReview(any());

        String attemptId = service.submitTask(SubmitXhsPublishTaskDTO.builder().taskId("task-a1").triggerType("submit").build());

        assertEquals("attempt-a1", attemptId);
        verify(executionSupport, never()).execute(any(), any(), any(), any());

        ArgumentCaptor<XhsPublishReviewEntity> reviewCaptor = ArgumentCaptor.forClass(XhsPublishReviewEntity.class);
        verify(publishRepository).saveReview(reviewCaptor.capture());
        assertEquals(ReviewStageVO.copy_review.name(), reviewCaptor.getValue().getReviewStage());
        assertEquals(ReviewStatusVO.pending.name(), reviewCaptor.getValue().getReviewStatus());
    }

    @Test
    void shouldRejectCreateWhenTemplateOwnerMismatch() {
        when(taskAccessSupport.requireUserId()).thenReturn(1L);
        doNothing().when(taskAccessSupport).validateModeAndType("B", "image");
        when(templateRepository.queryByTemplateId("template-9")).thenReturn(XhsPublishTemplateEntity.builder()
                .templateId("template-9")
                .userId(999L)
                .templateStatus(1)
                .templateConfigJson("{\"title\":\"x\"}")
                .build());

        WorkException exception = assertThrows(WorkException.class, () -> service.createTask(CreateXhsPublishTaskDTO.builder()
                .taskName("任务")
                .publishMode("B")
                .publishType("image")
                .templateId("template-9")
                .requestJson("{}")
                .build()));

        assertEquals("模板不存在", exception.getMessage());
    }

    @Test
    void shouldResumeExecutionWhenACopyReviewApproved() {
        XhsPublishTaskEntity task = task("task-a2");
        task.setPublishMode(PublishModeVO.A.name());
        task.setTaskStatus(PublishTaskStatusVO.running.name());
        task.setCurrentStage(PublishStageVO.copy_review_pending.name());

        XhsPublishAttemptEntity attempt = attempt("attempt-a2", 1, PublishAttemptStatusVO.waiting_review.name());
        attempt.setStage(PublishStageVO.copy_review_pending.name());
        XhsPublishAccountBindingEntity binding = binding("binding-1");

        when(taskAccessSupport.queryOwnedTask("task-a2")).thenReturn(task);
        when(taskAccessSupport.requireUserId()).thenReturn(1L);
        when(idSupport.nextReviewId()).thenReturn("review-a2");
        when(publishRepository.queryAttemptByAttemptId("attempt-a2")).thenReturn(attempt);
        when(taskStateMachine.nextTaskStatus(PublishTaskStatusVO.running.name(), "copy_review_approved")).thenReturn(PublishTaskStatusVO.running.name());
        when(taskStateMachine.nextStage(PublishStageVO.copy_review_pending.name(), "copy_review_approved")).thenReturn(PublishStageVO.param_building.name());
        when(taskStateMachine.nextAttemptStatus(PublishAttemptStatusVO.waiting_review.name(), "copy_review_approved")).thenReturn(PublishAttemptStatusVO.running.name());
        when(bindingSupport.resolveBinding(1L, "binding-1")).thenReturn(binding);
        when(publishRepository.listAssetByTaskId("task-a2")).thenReturn(List.of());
        doNothing().when(publishRepository).saveReview(any());
        doNothing().when(publishRepository).saveTask(any());
        doNothing().when(publishRepository).saveAttempt(any());

        service.reviewTask(ReviewXhsPublishTaskDTO.builder()
                .taskId("task-a2")
                .attemptId("attempt-a2")
                .reviewStage(ReviewStageVO.copy_review.name())
                .reviewMode("manual")
                .reviewStatus(ReviewStatusVO.approved.name())
                .reviewComment("通过")
                .build());

        verify(executionSupport).execute(eq(task), eq(attempt), eq(binding), eq(List.of()));
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
        ArgumentCaptor<XhsPublishAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(XhsPublishAttemptEntity.class);
        verify(publishRepository).saveAttempt(attemptCaptor.capture());
        assertEquals(BigDecimal.ZERO, attemptCaptor.getValue().getCostAmount());
        assertEquals(0L, attemptCaptor.getValue().getDurationMs());
        verify(executionSupport).execute(eq(task), eq(persistedAttempt), eq(binding), eq(List.of()));
    }

    @Test
    void shouldRejectRetryWhenNoAttemptHistory() {
        XhsPublishTaskEntity task = task("task-1");
        when(taskAccessSupport.queryOwnedTask("task-1")).thenReturn(task);
        when(publishRepository.queryLatestAttemptByTaskId("task-1")).thenReturn(null);

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

        XhsPublishAttemptStatsEntity attemptStats = XhsPublishAttemptStatsEntity.builder()
                .attemptCount(1)
                .maxAttemptNo(1)
                .totalDurationMs(300L)
                .totalCostAmount(new BigDecimal("0.6"))
                .build();
        XhsPublishAccountBindingEntity binding = binding("binding-1");
        XhsPublishAttemptEntity persistedAttempt = attempt("attempt-2", 2, PublishAttemptStatusVO.created.name());

        when(taskAccessSupport.queryOwnedTask("task-1")).thenReturn(task);
        when(publishRepository.queryLatestAttemptByTaskId("task-1")).thenReturn(latestAttempt);
        when(publishRepository.queryAttemptStatsByTaskId("task-1")).thenReturn(attemptStats);
        when(retryDomainService.evaluate(any())).thenReturn(
                RetryDecisionResult.allow(RetryDecisionReasonCode.ALLOW_WITHIN_POLICY, "ok")
        );
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

        verify(retryDomainService).evaluate(any());
        verify(executionSupport).execute(eq(task), eq(persistedAttempt), eq(binding), eq(List.of()));
    }

    @Test
    void shouldRejectRetryWhenLatestAttemptIsActive() {
        XhsPublishTaskEntity task = task("task-1");
        task.setRetryPolicyJson("{\"maxRetry\":3}");

        XhsPublishAttemptEntity latestAttempt = attempt("attempt-running", 2, PublishAttemptStatusVO.running.name());
        XhsPublishAttemptStatsEntity attemptStats = XhsPublishAttemptStatsEntity.builder()
                .attemptCount(2)
                .maxAttemptNo(2)
                .totalDurationMs(200L)
                .totalCostAmount(new BigDecimal("0.5"))
                .build();

        when(taskAccessSupport.queryOwnedTask("task-1")).thenReturn(task);
        when(publishRepository.queryLatestAttemptByTaskId("task-1")).thenReturn(latestAttempt);
        when(publishRepository.queryAttemptStatsByTaskId("task-1")).thenReturn(attemptStats);
        when(retryDomainService.evaluate(any())).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            assertTrue(arg instanceof RetryDecisionContext);
            RetryDecisionContext context = (RetryDecisionContext) arg;
            return Boolean.TRUE.equals(context.getActiveAttempt())
                    ? RetryDecisionResult.reject(RetryDecisionReasonCode.REJECT_ACTIVE_ATTEMPT, "active")
                    : RetryDecisionResult.allow(RetryDecisionReasonCode.ALLOW_WITHIN_POLICY, "ok");
        });

        RetryXhsPublishTaskDTO dto = RetryXhsPublishTaskDTO.builder().taskId("task-1").triggerType("manual_retry").build();
        WorkException exception = assertThrows(WorkException.class, () -> service.retryTask(dto));
        assertTrue(exception.getMessage().contains(RetryDecisionReasonCode.REJECT_ACTIVE_ATTEMPT));
    }

    @Test
    void shouldUseAttemptStatsMaxAttemptNoWhenCreateRetryAttempt() {
        XhsPublishTaskEntity task = task("task-1");
        task.setRetryPolicyJson("{\"maxRetry\":6}");
        task.setTaskStatus(PublishTaskStatusVO.failed.name());
        task.setCurrentStage(PublishStageVO.failed_terminal.name());

        XhsPublishAttemptEntity latestAttempt = attempt("attempt-5", 2, PublishAttemptStatusVO.failed.name());
        XhsPublishAttemptStatsEntity attemptStats = XhsPublishAttemptStatsEntity.builder()
                .attemptCount(7)
                .maxAttemptNo(7)
                .totalDurationMs(1000L)
                .totalCostAmount(new BigDecimal("1.2"))
                .build();
        XhsPublishAccountBindingEntity binding = binding("binding-1");
        XhsPublishAttemptEntity persistedAttempt = attempt("attempt-8", 8, PublishAttemptStatusVO.created.name());

        when(taskAccessSupport.queryOwnedTask("task-1")).thenReturn(task);
        when(publishRepository.queryLatestAttemptByTaskId("task-1")).thenReturn(latestAttempt);
        when(publishRepository.queryAttemptStatsByTaskId("task-1")).thenReturn(attemptStats);
        when(retryDomainService.evaluate(any())).thenReturn(
                RetryDecisionResult.allow(RetryDecisionReasonCode.ALLOW_WITHIN_POLICY, "ok")
        );
        when(bindingSupport.resolveBinding(1L, "binding-1")).thenReturn(binding);
        when(idSupport.nextAttemptId()).thenReturn("attempt-8");
        when(publishRepository.queryAttemptByAttemptId("attempt-8")).thenReturn(persistedAttempt);
        when(taskStateMachine.nextTaskStatus(PublishTaskStatusVO.failed.name(), "retry_requested")).thenReturn(PublishTaskStatusVO.running.name());
        when(taskStateMachine.nextStage(PublishStageVO.failed_terminal.name(), "start_execute")).thenReturn(PublishStageVO.planning.name());
        when(publishRepository.listAssetByTaskId("task-1")).thenReturn(List.of());
        doNothing().when(publishRepository).saveTask(any());
        doNothing().when(publishRepository).saveAttempt(any());

        service.retryTask(RetryXhsPublishTaskDTO.builder().taskId("task-1").triggerType("manual_retry").build());

        ArgumentCaptor<XhsPublishAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(XhsPublishAttemptEntity.class);
        verify(publishRepository, times(1)).saveAttempt(attemptCaptor.capture());
        assertEquals(8, attemptCaptor.getValue().getAttemptNo());
    }

    @Test
    void shouldDriveStateToFailedWhenAReviewRejected() {
        XhsPublishTaskEntity task = task("task-1");
        task.setPublishMode(PublishModeVO.A.name());
        task.setTaskStatus(PublishTaskStatusVO.running.name());
        task.setCurrentStage(PublishStageVO.pre_publish_review_pending.name());

        XhsPublishAttemptEntity attempt = attempt("attempt-review", 1, PublishAttemptStatusVO.waiting_review.name());
        attempt.setStage(PublishStageVO.pre_publish_review_pending.name());

        when(taskAccessSupport.queryOwnedTask("task-1")).thenReturn(task);
        when(taskAccessSupport.requireUserId()).thenReturn(1001L);
        when(idSupport.nextReviewId()).thenReturn("review-1");
        when(publishRepository.queryAttemptByAttemptId("attempt-review")).thenReturn(attempt);
        when(taskStateMachine.nextTaskStatus(PublishTaskStatusVO.running.name(), "validation_rejected")).thenReturn(PublishTaskStatusVO.failed.name());
        when(taskStateMachine.nextStage(PublishStageVO.pre_publish_review_pending.name(), "validation_rejected")).thenReturn(PublishStageVO.failed_terminal.name());
        when(taskStateMachine.nextAttemptStatus(PublishAttemptStatusVO.waiting_review.name(), "validation_rejected")).thenReturn(PublishAttemptStatusVO.failed.name());
        doNothing().when(publishRepository).saveReview(any());
        doNothing().when(publishRepository).saveTask(any());
        doNothing().when(publishRepository).saveAttempt(any());

        service.reviewTask(ReviewXhsPublishTaskDTO.builder()
                .taskId("task-1")
                .attemptId("attempt-review")
                .reviewStage(ReviewStageVO.pre_publish_review.name())
                .reviewMode("manual")
                .reviewStatus(ReviewStatusVO.rejected.name())
                .reviewComment("内容不合规")
                .build());

        ArgumentCaptor<XhsPublishTaskEntity> taskCaptor = ArgumentCaptor.forClass(XhsPublishTaskEntity.class);
        verify(publishRepository, times(1)).saveTask(taskCaptor.capture());
        assertEquals(PublishTaskStatusVO.failed.name(), taskCaptor.getValue().getTaskStatus());
        assertEquals(PublishStageVO.failed_terminal.name(), taskCaptor.getValue().getCurrentStage());

        ArgumentCaptor<XhsPublishAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(XhsPublishAttemptEntity.class);
        verify(publishRepository, times(1)).saveAttempt(attemptCaptor.capture());
        assertEquals(PublishAttemptStatusVO.failed.name(), attemptCaptor.getValue().getAttemptStatus());
        assertEquals("REVIEW_REJECTED", attemptCaptor.getValue().getErrorCode());
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
