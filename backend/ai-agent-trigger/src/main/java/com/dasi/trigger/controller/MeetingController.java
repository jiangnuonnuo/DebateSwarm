package com.dasi.trigger.controller;

import com.dasi.api.IMeetingService;
import com.dasi.api.dto.request.MeetingActionRequest;
import com.dasi.api.dto.request.MeetingCreateRequest;
import com.dasi.api.dto.request.MeetingStartRequest;
import com.dasi.api.dto.response.MeetingActionDTO;
import com.dasi.api.dto.response.MeetingIssueDTO;
import com.dasi.api.dto.response.MeetingStatusDTO;
import com.dasi.api.dto.response.MeetingSummaryDTO;
import com.dasi.domain.room.model.entity.MeetingActionEntity;
import com.dasi.domain.room.model.entity.MeetingIssueEntity;
import com.dasi.domain.room.model.valobj.MeetingStatusVO;
import com.dasi.domain.room.model.valobj.MeetingSummaryVO;
import com.dasi.domain.room.service.meeting.IMeetingGovernanceService;
import com.dasi.types.result.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/chat-room/meeting")
public class MeetingController implements IMeetingService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Resource
    private IMeetingGovernanceService meetingGovernanceService;

    @Override
    @PostMapping("/create")
    public Result<String> createMeeting(@RequestBody MeetingCreateRequest request) {
        try {
            log.info("【MEETING_API_CREATE】roomId={}, creatorId={}, creatorRole={}, pmId={}",
                    request.getRoomId(), request.getCreatorId(), request.getCreatorRole(), request.getPmId());
            String meetingId = meetingGovernanceService.createMeeting(
                    request.getRoomId(),
                    request.getTopic(),
                    request.getGoal(),
                    request.getCreatorId(),
                    request.getCreatorRole(),
                    request.getPmId(),
                    request.getRoleAssignments()
            );
            log.info("【MEETING_API_CREATE_SUCCESS】roomId={}, meetingId={}", request.getRoomId(), meetingId);
            return Result.success(meetingId);
        } catch (Exception e) {
            log.error("【MEETING_API_CREATE_FAIL】roomId={}, creatorId={}, error={}",
                    request.getRoomId(), request.getCreatorId(), e.getMessage(), e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    @PostMapping("/start")
    public Result<Boolean> startMeeting(@RequestBody MeetingStartRequest request) {
        try {
            log.info("【MEETING_API_START】roomId={}, meetingId={}, actorId={}, actorRole={}, expectedVersion={}",
                    request.getRoomId(), request.getMeetingId(), request.getActorId(), request.getActorRole(), request.getExpectedVersion());
            meetingGovernanceService.startMeeting(
                    request.getRoomId(),
                    request.getMeetingId(),
                    request.getActorId(),
                    request.getActorRole(),
                    request.getExpectedVersion()
            );
            log.info("【MEETING_API_START_SUCCESS】roomId={}, meetingId={}, actorId={}",
                    request.getRoomId(), request.getMeetingId(), request.getActorId());
            return Result.success(Boolean.TRUE);
        } catch (Exception e) {
            log.error("【MEETING_API_START_FAIL】roomId={}, meetingId={}, actorId={}, error={}",
                    request.getRoomId(), request.getMeetingId(), request.getActorId(), e.getMessage(), e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    @PostMapping("/action")
    public Result<Boolean> applyAction(@RequestBody MeetingActionRequest request) {
        try {
            log.info("【MEETING_API_ACTION】roomId={}, meetingId={}, actionType={}, actorId={}, actorRole={}, targetId={}, targetRole={}, expectedVersion={}, clientRequestId={}, traceId={}",
                    request.getRoomId(), request.getMeetingId(), request.getActionType(), request.getActorId(), request.getActorRole(),
                    request.getTargetId(), request.getTargetRole(), request.getExpectedVersion(), request.getClientRequestId(), request.getTraceId());
            meetingGovernanceService.applyAction(
                    request.getRoomId(),
                    request.getMeetingId(),
                    request.getActionType(),
                    request.getActorId(),
                    request.getActorRole(),
                    request.getTargetId(),
                    request.getTargetRole(),
                    request.getPayload(),
                    request.getReason(),
                    request.getRequiredFix(),
                    request.getDeadline(),
                    request.getClientRequestId(),
                    request.getTraceId(),
                    request.getExpectedVersion()
            );
            log.info("【MEETING_API_ACTION_SUCCESS】roomId={}, meetingId={}, actionType={}, actorId={}, clientRequestId={}",
                    request.getRoomId(), request.getMeetingId(), request.getActionType(), request.getActorId(), request.getClientRequestId());
            return Result.success(Boolean.TRUE);
        } catch (Exception e) {
            log.error("【MEETING_API_ACTION_FAIL】roomId={}, meetingId={}, actionType={}, actorId={}, clientRequestId={}, error={}",
                    request.getRoomId(), request.getMeetingId(), request.getActionType(), request.getActorId(), request.getClientRequestId(), e.getMessage(), e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    @GetMapping("/status")
    public Result<MeetingStatusDTO> queryMeetingStatus(@RequestParam("roomId") String roomId,
                                                       @RequestParam(value = "meetingId", required = false) String meetingId) {
        try {
            MeetingStatusVO statusVO = meetingGovernanceService.queryMeetingStatus(roomId, meetingId);
            if (statusVO == null) {
                log.info("【MEETING_API_STATUS_EMPTY】roomId={}, meetingId={}", roomId, meetingId);
                return Result.success(null);
            }
            log.info("【MEETING_API_STATUS_SUCCESS】roomId={}, meetingId={}, status={}, version={}",
                    roomId, statusVO.getMeetingId(), statusVO.getStatus() == null ? null : statusVO.getStatus().getCode(), statusVO.getVersion());
            return Result.success(convertStatus(statusVO));
        } catch (Exception e) {
            log.error("【MEETING_API_STATUS_FAIL】roomId={}, meetingId={}, error={}", roomId, meetingId, e.getMessage(), e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    @GetMapping("/timeline")
    public Result<List<MeetingActionDTO>> queryMeetingTimeline(@RequestParam("roomId") String roomId,
                                                               @RequestParam(value = "meetingId", required = false) String meetingId) {
        try {
            List<MeetingActionEntity> timeline = meetingGovernanceService.queryMeetingTimeline(roomId, meetingId);
            log.info("【MEETING_API_TIMELINE_SUCCESS】roomId={}, meetingId={}, size={}", roomId, meetingId, timeline == null ? 0 : timeline.size());
            return Result.success(convertTimeline(timeline));
        } catch (Exception e) {
            log.error("【MEETING_API_TIMELINE_FAIL】roomId={}, meetingId={}, error={}", roomId, meetingId, e.getMessage(), e);
            return Result.error(e.getMessage());
        }
    }

    private MeetingStatusDTO convertStatus(MeetingStatusVO statusVO) {
        return MeetingStatusDTO.builder()
                .meetingId(statusVO.getMeetingId())
                .roomId(statusVO.getRoomId())
                .topic(statusVO.getTopic())
                .goal(statusVO.getGoal())
                .status(statusVO.getStatus() == null ? null : statusVO.getStatus().getCode())
                .roundNumber(statusVO.getRoundNumber())
                .version(statusVO.getVersion())
                .ownerId(statusVO.getOwnerId())
                .ownerRole(statusVO.getOwnerRole())
                .pmId(statusVO.getPmId())
                .roleAssignments(statusVO.getRoleAssignments())
                .summary(convertSummary(statusVO.getSummary()))
                .openIssues(convertIssues(statusVO.getOpenIssues()))
                .timelineSize(statusVO.getTimelineSize())
                .build();
    }

    private MeetingSummaryDTO convertSummary(MeetingSummaryVO summaryVO) {
        if (summaryVO == null) {
            return null;
        }
        return MeetingSummaryDTO.builder()
                .conclusion(summaryVO.getConclusion())
                .actionItems(summaryVO.getActionItems())
                .nextRoundCondition(summaryVO.getNextRoundCondition())
                .concludedByRole(summaryVO.getConcludedByRole())
                .concludedAt(summaryVO.getConcludedAt() == null ? null : summaryVO.getConcludedAt().format(TIME_FORMATTER))
                .build();
    }

    private List<MeetingIssueDTO> convertIssues(List<MeetingIssueEntity> issues) {
        if (issues == null || issues.isEmpty()) {
            return new ArrayList<>();
        }
        return issues.stream()
                .map(issue -> MeetingIssueDTO.builder()
                        .issueId(issue.getIssueId())
                        .reporterRole(issue.getReporterRole())
                        .assigneeRole(issue.getAssigneeRole())
                        .reason(issue.getReason())
                        .requiredFix(issue.getRequiredFix())
                        .dueAt(issue.getDueAt() == null ? null : issue.getDueAt().format(TIME_FORMATTER))
                        .status(issue.getStatus())
                        .createdAt(issue.getCreatedAt() == null ? null : issue.getCreatedAt().format(TIME_FORMATTER))
                        .resolvedAt(issue.getResolvedAt() == null ? null : issue.getResolvedAt().format(TIME_FORMATTER))
                        .build())
                .collect(Collectors.toList());
    }

    private List<MeetingActionDTO> convertTimeline(List<MeetingActionEntity> timeline) {
        if (timeline == null || timeline.isEmpty()) {
            return new ArrayList<>();
        }
        return timeline.stream()
                .map(action -> MeetingActionDTO.builder()
                        .actionId(action.getActionId())
                        .actionType(action.getActionType() == null ? null : action.getActionType().getCode())
                        .actorId(action.getActorId())
                        .actorRole(action.getActorRole())
                        .targetId(action.getTargetId())
                        .targetRole(action.getTargetRole())
                        .payload(action.getPayload())
                        .reason(action.getReason())
                        .requiredFix(action.getRequiredFix())
                        .deadline(action.getDeadline() == null ? null : action.getDeadline().format(TIME_FORMATTER))
                        .result(action.getResult())
                        .meetingVersion(action.getMeetingVersion())
                        .clientRequestId(action.getClientRequestId())
                        .traceId(action.getTraceId())
                        .createdAt(action.getCreatedAt() == null ? null : action.getCreatedAt().format(TIME_FORMATTER))
                        .build())
                .collect(Collectors.toList());
    }
}
