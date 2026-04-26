package com.dasi.domain.xhspublish.service.domain.impl;

import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTaskStatusVO;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import org.springframework.stereotype.Service;

@Service
public class PublishTaskStateMachineImpl implements IPublishTaskStateMachine {

    @Override
    public String nextTaskStatus(String currentStatus, String event) {
        if ("start_execute".equals(event)) {
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
        if ("start_execute".equals(event)) {
            return PublishStageVO.planning.name();
        }
        if ("payload_built".equals(event)) {
            return PublishStageVO.param_building.name();
        }
        if ("publish_submitted".equals(event) || "publish_accepted".equals(event) || "publish_succeeded".equals(event) || "publish_failed".equals(event)) {
            return PublishStageVO.publishing.name();
        }
        return currentStage == null ? PublishStageVO.planning.name() : currentStage;
    }

}
