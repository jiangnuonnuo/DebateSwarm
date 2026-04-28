package com.dasi.domain.xhspublish.service.domain.impl;

import com.dasi.domain.xhspublish.model.valobj.PublishAttemptStatusVO;
import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTaskStatusVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublishTaskStateMachineImplTest {

    private final PublishTaskStateMachineImpl stateMachine = new PublishTaskStateMachineImpl();

    @Test
    void shouldMapSubmitPublishAndTerminalEvents() {
        assertEquals(PublishTaskStatusVO.running.name(), stateMachine.nextTaskStatus(PublishTaskStatusVO.draft.name(), "submit_requested"));
        assertEquals(PublishStageVO.param_building.name(), stateMachine.nextStage(PublishStageVO.planning.name(), "payload_built"));
        assertEquals(PublishStageVO.publishing.name(), stateMachine.nextStage(PublishStageVO.param_building.name(), "publish_submitted"));
        assertEquals(PublishTaskStatusVO.published.name(), stateMachine.nextTaskStatus(PublishTaskStatusVO.running.name(), "publish_succeeded"));
        assertEquals(PublishStageVO.completed.name(), stateMachine.nextStage(PublishStageVO.publishing.name(), "publish_succeeded"));
    }

    @Test
    void shouldMapAttemptStatesForMainFlow() {
        assertEquals(PublishAttemptStatusVO.running.name(), stateMachine.nextAttemptStatus(PublishAttemptStatusVO.created.name(), "start_execute"));
        assertEquals(PublishAttemptStatusVO.accepted.name(), stateMachine.nextAttemptStatus(PublishAttemptStatusVO.running.name(), "publish_accepted"));
        assertEquals(PublishAttemptStatusVO.published.name(), stateMachine.nextAttemptStatus(PublishAttemptStatusVO.accepted.name(), "publish_succeeded"));
        assertEquals(PublishAttemptStatusVO.failed.name(), stateMachine.nextAttemptStatus(PublishAttemptStatusVO.running.name(), "publish_failed"));
    }

}

