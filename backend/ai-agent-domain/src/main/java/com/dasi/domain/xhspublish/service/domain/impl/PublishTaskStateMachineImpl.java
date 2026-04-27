package com.dasi.domain.xhspublish.service.domain.impl;

import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.model.valobj.PublishAttemptStatusVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTaskStatusVO;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import org.springframework.stereotype.Service;

@Service
public class PublishTaskStateMachineImpl implements IPublishTaskStateMachine {

    @Override
    public String nextTaskStatus(String currentStatus, String event) {
        if ("start_execute".equals(event) || "submit_requested".equals(event)) {
            return PublishTaskStatusVO.running.name();
        }
        if ("publish_accepted".equals(event)) {
            return PublishTaskStatusVO.accepted.name();
        }
        if ("publish_succeeded".equals(event)) {
            return PublishTaskStatusVO.published.name();
        }
        if ("publish_failed".equals(event)) {
            return PublishTaskStatusVO.failed.name();
        }
        if ("retry_requested".equals(event)) {
            return PublishTaskStatusVO.running.name();
        }
        return currentStatus == null ? PublishTaskStatusVO.draft.name() : currentStatus;
    }

    @Override
    public String nextStage(String currentStage, String event) {
        if ("start_execute".equals(event) || "submit_requested".equals(event)) {
            return PublishStageVO.planning.name();
        }
        if ("payload_built".equals(event)) {
            return PublishStageVO.param_building.name();
        }
        if ("copy_review_required".equals(event)) {
            return PublishStageVO.copy_review_pending.name();
        }
        if ("image_generation_required".equals(event)) {
            return PublishStageVO.image_generating.name();
        }
        if ("pre_publish_review_required".equals(event)) {
            return PublishStageVO.pre_publish_review_pending.name();
        }
        if ("publish_succeeded".equals(event)) {
            return PublishStageVO.completed.name();
        }
        if ("publish_failed".equals(event)) {
            return PublishStageVO.failed_terminal.name();
        }
        if ("cancel_requested".equals(event)) {
            return PublishStageVO.cancelled_terminal.name();
        }
        if ("publish_submitted".equals(event) || "publish_accepted".equals(event)) {
            return PublishStageVO.publishing.name();
        }
        return currentStage == null ? PublishStageVO.planning.name() : currentStage;
    }

    @Override
    public String nextAttemptStatus(String currentAttemptStatus, String event) {
        if ("attempt_created".equals(event)) {
            return PublishAttemptStatusVO.created.name();
        }
        if ("start_execute".equals(event) || "attempt_started".equals(event) || "submit_requested".equals(event)) {
            return PublishAttemptStatusVO.running.name();
        }
        if ("copy_review_required".equals(event) || "pre_publish_review_required".equals(event) || "attempt_waiting_review".equals(event)) {
            return PublishAttemptStatusVO.waiting_review.name();
        }
        if ("copy_review_approved".equals(event) || "pre_publish_review_approved".equals(event) || "attempt_resumed".equals(event)) {
            return PublishAttemptStatusVO.running.name();
        }
        if ("publish_accepted".equals(event) || "attempt_accepted".equals(event)) {
            return PublishAttemptStatusVO.accepted.name();
        }
        if ("publish_succeeded".equals(event) || "attempt_published".equals(event)) {
            return PublishAttemptStatusVO.published.name();
        }
        if ("publish_failed".equals(event) || "validation_rejected".equals(event) || "attempt_failed".equals(event)) {
            return PublishAttemptStatusVO.failed.name();
        }
        if ("cancel_requested".equals(event) || "attempt_cancelled".equals(event)) {
            return PublishAttemptStatusVO.cancelled.name();
        }
        return currentAttemptStatus == null ? PublishAttemptStatusVO.created.name() : currentAttemptStatus;
    }

}
