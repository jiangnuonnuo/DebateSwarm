package com.dasi.domain.xhspublish.service.domain;

public interface IPublishTaskStateMachine {

    String nextTaskStatus(String currentStatus, String event);

    String nextStage(String currentStage, String event);

    String nextAttemptStatus(String currentAttemptStatus, String event);

}
