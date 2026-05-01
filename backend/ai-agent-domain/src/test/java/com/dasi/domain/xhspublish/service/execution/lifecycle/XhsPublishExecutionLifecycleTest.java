package com.dasi.domain.xhspublish.service.execution.lifecycle;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTaskStatusVO;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.knowledge.XhsPublishKnowledgeIngestionService;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishExecutionLifecycleTest {

    private XhsPublishExecutionLifecycle lifecycle;

    @Mock
    private IXhsPublishRepository publishRepository;

    @Mock
    private IPublishTaskStateMachine taskStateMachine;

    @Mock
    private XhsPublishKnowledgeIngestionService knowledgeIngestionService;

    @BeforeEach
    void setUp() {
        lifecycle = new XhsPublishExecutionLifecycle();
        ReflectionTestUtils.setField(lifecycle, "publishRepository", publishRepository);
        ReflectionTestUtils.setField(lifecycle, "taskStateMachine", taskStateMachine);
        ReflectionTestUtils.setField(lifecycle, "knowledgeIngestionService", knowledgeIngestionService);
        doNothing().when(publishRepository).saveTask(any());
        doNothing().when(publishRepository).saveAttempt(any());
    }

    @Test
    void shouldMarkPublishFailedAndKeepRetryableAttempt() {
        XhsPublishExecutionContext context = context();
        XhsPublishRemoteResult remoteResult = XhsPublishRemoteResult.builder()
                .status("failed")
                .resultJson("{\"status\":\"failed\"}")
                .errorCode("MCP_TRANSIENT_FAIL")
                .errorMessage("timeout")
                .build();
        when(taskStateMachine.nextTaskStatus(PublishTaskStatusVO.running.name(), "publish_failed")).thenReturn(PublishTaskStatusVO.failed.name());
        when(taskStateMachine.nextStage(PublishStageVO.publishing.name(), "publish_failed")).thenReturn(PublishStageVO.failed_terminal.name());
        when(taskStateMachine.nextAttemptStatus("running", "publish_failed")).thenReturn("failed");

        lifecycle.markPublishFailed(context, remoteResult, LocalDateTime.now().minusSeconds(1));

        assertEquals(PublishTaskStatusVO.failed.name(), context.getTask().getTaskStatus());
        assertEquals(PublishStageVO.failed_terminal.name(), context.getTask().getCurrentStage());
        assertEquals("failed", context.getAttempt().getAttemptStatus());
        assertEquals(1, context.getAttempt().getRetryable());
        verify(publishRepository).saveTask(context.getTask());
        verify(publishRepository).saveAttempt(context.getAttempt());
        verify(knowledgeIngestionService, never()).ingestPublishSuccess(any(), any(), any());
    }

    @Test
    void shouldMarkPublishSucceededAndCloseAttempt() {
        XhsPublishExecutionContext context = context();
        XhsPublishRemoteResult remoteResult = XhsPublishRemoteResult.builder()
                .status("published")
                .resultJson("{\"status\":\"published\"}")
                .build();
        when(taskStateMachine.nextTaskStatus(PublishTaskStatusVO.running.name(), "publish_succeeded")).thenReturn(PublishTaskStatusVO.published.name());
        when(taskStateMachine.nextStage(PublishStageVO.publishing.name(), "publish_succeeded")).thenReturn(PublishStageVO.completed.name());
        when(taskStateMachine.nextAttemptStatus("running", "publish_succeeded")).thenReturn("published");

        lifecycle.markPublishSucceeded(context, remoteResult, LocalDateTime.now().minusSeconds(1));

        assertEquals(PublishTaskStatusVO.published.name(), context.getTask().getTaskStatus());
        assertEquals(PublishStageVO.completed.name(), context.getTask().getCurrentStage());
        assertEquals("published", context.getAttempt().getAttemptStatus());
        assertEquals(0, context.getAttempt().getRetryable());
        verify(publishRepository).saveTask(context.getTask());
        verify(publishRepository).saveAttempt(context.getAttempt());
        verify(knowledgeIngestionService).ingestPublishSuccess(context.getTask(), context.getAttempt(), remoteResult);
    }

    private XhsPublishExecutionContext context() {
        XhsPublishTaskEntity task = XhsPublishTaskEntity.builder()
                .taskStatus(PublishTaskStatusVO.running.name())
                .currentStage(PublishStageVO.publishing.name())
                .build();
        XhsPublishAttemptEntity attempt = XhsPublishAttemptEntity.builder()
                .attemptStatus("running")
                .build();
        return XhsPublishExecutionContext.builder()
                .task(task)
                .attempt(attempt)
                .build();
    }

}
