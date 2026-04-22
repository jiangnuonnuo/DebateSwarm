package com.dasi.domain.room.service.meeting;

import com.dasi.domain.room.model.entity.MeetingActionEntity;
import com.dasi.domain.room.model.valobj.MeetingStatusVO;

import java.util.List;
import java.util.Map;

public interface IMeetingGovernanceService {

    String createMeeting(String roomId,
                         String topic,
                         String goal,
                         String creatorId,
                         String creatorRole,
                         String pmId,
                         Map<String, String> roleAssignments);

    void startMeeting(String roomId,
                      String meetingId,
                      String actorId,
                      String actorRole,
                      Integer expectedVersion);

    void applyAction(String roomId,
                     String meetingId,
                     String actionType,
                     String actorId,
                     String actorRole,
                     String targetId,
                     String targetRole,
                     String payload,
                     String reason,
                     String requiredFix,
                     String deadline,
                     String clientRequestId,
                     String traceId,
                     Integer expectedVersion);

    MeetingStatusVO queryMeetingStatus(String roomId, String meetingId);

    List<MeetingActionEntity> queryMeetingTimeline(String roomId, String meetingId);
}
