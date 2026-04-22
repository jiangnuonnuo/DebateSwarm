package com.dasi.domain.room.service.meeting;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.apapter.repository.IMeetingRepository;
import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.MeetingActionEntity;
import com.dasi.domain.room.model.entity.MeetingIssueEntity;
import com.dasi.domain.room.model.entity.MeetingSessionEntity;
import com.dasi.domain.room.model.valobj.MeetingActionType;
import com.dasi.domain.room.model.valobj.MeetingStatus;
import com.dasi.domain.room.model.valobj.MeetingStatusVO;
import com.dasi.domain.room.model.valobj.MeetingSummaryVO;
import com.dasi.domain.room.service.IRoomChatService;
import com.dasi.types.exception.AuthException;
import com.dasi.types.exception.DependencyConflictException;
import com.dasi.types.exception.MissingException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 真实会议治理服务。
 * 该服务负责会议状态机、动作鉴权、问题单与结论协同，不负责 DAO 细节。
 */
@Slf4j
@Service
public class MeetingGovernanceService implements IMeetingGovernanceService {

    private static final String ISSUE_STATUS_OPEN = "OPEN";
    private static final String NOTICE_MEETING_CREATED = "MEETING_CREATED";
    private static final String NOTICE_ACTION_APPLIED = "MEETING_ACTION_APPLIED";
    private static final String NOTICE_STAGE_CHANGED = "MEETING_STAGE_CHANGED";
    private static final String ACTION_RESULT_CREATED = "CREATED";
    private static final String ACTION_RESULT_APPLIED = "APPLIED";
    private static final String DEFAULT_MEETING_TOPIC = "未命名会议";
    private static final String DEFAULT_MEETING_GOAL = "待补充目标";
    private static final String DEFAULT_RETURN_REASON = "需补充信息";
    private static final String DEFAULT_REQUIRED_FIX = "请按评审意见修订并重新提交";

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Resource
    private IMeetingRepository meetingRepository;

    @Resource
    private IRoomChatService roomChatService;

    @Override
    @Transactional
    public String createMeeting(String roomId,
                                String topic,
                                String goal,
                                String creatorId,
                                String creatorRole,
                                String pmId,
                                Map<String, String> roleAssignments) {
        log.info("【MEETING_CREATE_START】roomId={}, creatorId={}, creatorRole={}, pmId={}",
                roomId, creatorId, creatorRole, pmId);

        validateCreateCommand(roomId, creatorId);
        ensureNoActiveMeeting(roomId);

        LocalDateTime now = LocalDateTime.now();
        String meetingId = buildMeetingId();
        MeetingSessionEntity meeting = buildInitialMeeting(
                meetingId, roomId, topic, goal, creatorId, creatorRole, pmId, roleAssignments, now
        );

        meetingRepository.saveMeeting(meeting);
        persistCreateAction(meeting, creatorId, now);
        publishMeetingNotice(meeting, NOTICE_MEETING_CREATED, "会议已创建，等待主持人开始。", null, null, meeting.getVersion());

        log.info("【MEETING_CREATE_SUCCESS】roomId={}, meetingId={}, status={}, version={}",
                roomId, meetingId, meeting.getStatus().getCode(), meeting.getVersion());
        return meetingId;
    }

    @Override
    @Transactional
    public void startMeeting(String roomId,
                             String meetingId,
                             String actorId,
                             String actorRole,
                             Integer expectedVersion) {
        log.info("【MEETING_START_REQUEST】roomId={}, meetingId={}, actorId={}, actorRole={}, expectedVersion={}",
                roomId, meetingId, actorId, actorRole, expectedVersion);

        requireExpectedVersion(expectedVersion);
        MeetingSessionEntity meeting = getRequiredMeeting(roomId, meetingId);
        String normalizedRole = normalizeRole(actorRole, "");

        ensureActionPermission(meeting, MeetingActionType.START, actorId, normalizedRole);
        ensureStartState(meeting);

        // M3: 开始会议时首轮必须为 1（若历史值已大于 1，则保持原值）。
        meeting.setRoundNumber(resolveStartRoundNumber(meeting.getRoundNumber()));
        boolean changed = applyLifecycleUpdate(meeting, MeetingStatus.IN_PROGRESS, expectedVersion);
        if (!changed) {
            throw new DependencyConflictException("会议版本已变化，请刷新后重试");
        }
        MeetingActionEntity action = buildActionEntity(meeting, MeetingActionType.START, actorId, normalizedRole,
                null, null, null, null, null, null, null, null, meeting.getVersion());
        saveActionOrFail(action);

        publishMeetingNotice(meeting, NOTICE_STAGE_CHANGED, "会议已开始，当前状态：进行中。", null, null, meeting.getVersion());
        log.info("【MEETING_START_SUCCESS】roomId={}, meetingId={}, actorId={}, fromStatus=PENDING, toStatus={}, roundNumber={}, version={}",
                roomId, meetingId, actorId, meeting.getStatus().getCode(), meeting.getRoundNumber(), meeting.getVersion());
    }

    @Override
    @Transactional
    public void applyAction(String roomId,
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
                            Integer expectedVersion) {
        log.info("【MEETING_ACTION_REQUEST】roomId={}, meetingId={}, actionType={}, actorId={}, actorRole={}, targetId={}, targetRole={}, expectedVersion={}, clientRequestId={}, traceId={}",
                roomId, meetingId, actionType, actorId, actorRole, targetId, targetRole, expectedVersion, clientRequestId, traceId);

        requireExpectedVersion(expectedVersion);
        MeetingSessionEntity meeting = getRequiredMeeting(roomId, meetingId);
        MeetingActionType meetingActionType = parseActionType(actionType);
        String normalizedActorRole = normalizeRole(actorRole, "");
        String normalizedTargetRole = normalizeRole(targetRole, "");

        if (isIdempotentHandled(meeting.getMeetingId(), clientRequestId, traceId)) {
            return;
        }

        ensureTargetRoleRequired(meetingActionType, normalizedTargetRole);
        ensureActionPermission(meeting, meetingActionType, actorId, normalizedActorRole);
        ensureActionState(meeting, meetingActionType);

        ActionApplyContext context = prepareActionContext(
                meeting, meetingActionType, actorId, normalizedActorRole, targetId, normalizedTargetRole,
                payload, reason, requiredFix, deadline, clientRequestId, traceId
        );

        if (!updateMeetingStateWithIdempotentFallback(context, expectedVersion)) {
            return;
        }

        if (!saveActionWithIdempotentFallback(context)) {
            return;
        }

        persistSummaryAndIssue(context);
        publishActionNotice(context);
        log.info("【MEETING_ACTION_SUCCESS】roomId={}, meetingId={}, actionType={}, actorId={}, actorRole={}, fromStatus={}, toStatus={}, roundNumber={}, version={}, traceId={}",
                context.getMeeting().getRoomId(),
                context.getMeeting().getMeetingId(),
                context.getActionType().getCode(),
                context.getActorId(),
                context.getActorRole(),
                context.getBeforeStatus().getCode(),
                context.getAfterStatus().getCode(),
                context.getMeeting().getRoundNumber(),
                context.getMeeting().getVersion(),
                context.getTraceId());
    }

    @Override
    public MeetingStatusVO queryMeetingStatus(String roomId, String meetingId) {
        MeetingSessionEntity meeting = resolveMeeting(roomId, meetingId);
        if (meeting == null) {
            return null;
        }
        List<MeetingIssueEntity> openIssues = filterOpenIssues(meetingRepository.queryIssues(meeting.getMeetingId()));
        List<MeetingActionEntity> timeline = meetingRepository.queryActions(meeting.getMeetingId());
        MeetingSummaryVO summary = meetingRepository.querySummary(meeting.getMeetingId());
        return buildMeetingStatusView(meeting, summary, openIssues, timeline);
    }

    @Override
    public List<MeetingActionEntity> queryMeetingTimeline(String roomId, String meetingId) {
        MeetingSessionEntity meeting = resolveMeeting(roomId, meetingId);
        if (meeting == null) {
            return new ArrayList<>();
        }
        List<MeetingActionEntity> timeline = meetingRepository.queryActions(meeting.getMeetingId());
        return timeline == null ? new ArrayList<>() : timeline;
    }

    /**
     * 构造动作上下文，统一处理状态迁移和后续持久化输入。
     */
    private ActionApplyContext prepareActionContext(MeetingSessionEntity meeting,
                                                    MeetingActionType actionType,
                                                    String actorId,
                                                    String actorRole,
                                                    String targetId,
                                                    String targetRole,
                                                    String payload,
                                                    String reason,
                                                    String requiredFix,
                                                    String deadline,
                                                    String clientRequestId,
                                                    String traceId) {
        MeetingStatus beforeStatus = meeting.getStatus();
        MeetingStatus afterStatus = applyTransition(meeting, actionType);
        applyRoundAdvancePolicy(meeting, beforeStatus, actionType);
        MeetingSummaryVO summary = buildSummaryIfNeeded(actionType, actorRole, payload, reason, requiredFix);
        MeetingIssueEntity issue = buildIssueIfNeeded(meeting, actionType, actorRole, targetRole, reason, requiredFix, deadline);
        MeetingActionEntity action = buildActionEntity(
                meeting, actionType, actorId, actorRole, targetId, targetRole,
                payload, reason, requiredFix, deadline, clientRequestId, traceId, null
        );
        return ActionApplyContext.builder()
                .meeting(meeting)
                .actionType(actionType)
                .actorId(actorId)
                .actorRole(actorRole)
                .targetRole(targetRole)
                .beforeStatus(beforeStatus)
                .afterStatus(afterStatus)
                .summary(summary)
                .issue(issue)
                .action(action)
                .clientRequestId(clientRequestId)
                .traceId(traceId)
                .build();
    }

    /**
     * 先更新会议状态，再写动作流水，保证会议状态与流水版本一致。
     */
    private boolean updateMeetingStateWithIdempotentFallback(ActionApplyContext context, Integer expectedVersion) {
        boolean changed = applyLifecycleUpdate(context.getMeeting(), context.getAfterStatus(), expectedVersion);
        if (changed) {
            return true;
        }
        if (isIdempotentHandled(context.getMeeting().getMeetingId(), context.getClientRequestId(), context.getTraceId())) {
            return false;
        }
        throw new DependencyConflictException("会议版本已变化，请刷新后重试");
    }

    /**
     * 保存动作流水时，利用 clientRequestId 进行幂等去重。
     */
    private boolean saveActionWithIdempotentFallback(ActionApplyContext context) {
        context.getAction().setMeetingVersion(context.getMeeting().getVersion());
        boolean saved = meetingRepository.saveAction(context.getAction());
        if (saved) {
            return true;
        }
        if (isIdempotentHandled(context.getMeeting().getMeetingId(), context.getClientRequestId(), context.getTraceId())) {
            return false;
        }
        throw new DependencyConflictException("动作流水保存失败，请重试");
    }

    private void persistSummaryAndIssue(ActionApplyContext context) {
        if (context.getSummary() != null) {
            meetingRepository.saveSummary(context.getMeeting().getMeetingId(), context.getSummary());
        }
        if (context.getIssue() != null) {
            meetingRepository.saveIssue(context.getIssue());
        }
        if (shouldResolveIssues(context.getActionType())) {
            meetingRepository.resolveOpenIssues(context.getMeeting().getMeetingId(), context.getTargetRole());
        }
    }

    private boolean shouldResolveIssues(MeetingActionType actionType) {
        return MeetingActionType.RESUME.equals(actionType)
                || MeetingActionType.APPROVE.equals(actionType)
                || MeetingActionType.CONCLUDE.equals(actionType);
    }

    private MeetingStatusVO buildMeetingStatusView(MeetingSessionEntity meeting,
                                                   MeetingSummaryVO summary,
                                                   List<MeetingIssueEntity> openIssues,
                                                   List<MeetingActionEntity> timeline) {
        return MeetingStatusVO.builder()
                .meetingId(meeting.getMeetingId())
                .roomId(meeting.getRoomId())
                .topic(meeting.getTopic())
                .goal(meeting.getGoal())
                .status(meeting.getStatus())
                .roundNumber(meeting.getRoundNumber())
                .version(meeting.getVersion())
                .ownerId(meeting.getOwnerId())
                .ownerRole(meeting.getOwnerRole())
                .pmId(meeting.getPmId())
                .roleAssignments(meeting.getRoleAssignments())
                .summary(summary)
                .openIssues(openIssues)
                .timelineSize(timeline == null ? 0 : timeline.size())
                .build();
    }

    private boolean applyLifecycleUpdate(MeetingSessionEntity meeting, MeetingStatus toStatus, Integer expectedVersion) {
        meeting.setStatus(toStatus);
        meeting.setUpdateTime(LocalDateTime.now());
        boolean updated = meetingRepository.updateMeetingWithVersion(meeting, expectedVersion);
        if (!updated) {
            return false;
        }
        meeting.setVersion(expectedVersion + 1);
        return true;
    }

    private MeetingActionEntity buildActionEntity(MeetingSessionEntity meeting,
                                                  MeetingActionType actionType,
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
                                                  Integer meetingVersion) {
        return MeetingActionEntity.builder()
                .actionId(buildActionId())
                .meetingId(meeting.getMeetingId())
                .roomId(meeting.getRoomId())
                .actionType(actionType)
                .actorId(actorId)
                .actorRole(actorRole)
                .targetId(targetId)
                .targetRole(targetRole)
                .payload(payload)
                .reason(reason)
                .requiredFix(requiredFix)
                .deadline(parseDeadline(deadline))
                .result(resolveActionResult(actionType))
                .meetingVersion(meetingVersion)
                .clientRequestId(clientRequestId)
                .traceId(traceId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String resolveActionResult(MeetingActionType actionType) {
        return ACTION_RESULT_APPLIED;
    }

    private void persistCreateAction(MeetingSessionEntity meeting, String creatorId, LocalDateTime now) {
        MeetingActionEntity action = MeetingActionEntity.builder()
                .actionId(buildActionId())
                .meetingId(meeting.getMeetingId())
                .roomId(meeting.getRoomId())
                .actionType(MeetingActionType.START)
                .actorId(creatorId)
                .actorRole(meeting.getOwnerRole())
                .payload("MEETING_CREATED")
                .result(ACTION_RESULT_CREATED)
                .meetingVersion(meeting.getVersion())
                .createdAt(now)
                .build();
        saveActionOrFail(action);
    }

    private void saveActionOrFail(MeetingActionEntity action) {
        boolean saved = meetingRepository.saveAction(action);
        if (!saved) {
            throw new DependencyConflictException("动作流水已存在，重复提交被拒绝");
        }
    }

    private void publishActionNotice(ActionApplyContext context) {
        String content = String.format("会议动作已执行：%s（%s -> %s）",
                context.getActionType().getCode(),
                context.getBeforeStatus().getCode(),
                context.getAfterStatus().getCode());
        publishMeetingNotice(
                context.getMeeting(),
                NOTICE_ACTION_APPLIED,
                content,
                context.getClientRequestId(),
                context.getTraceId(),
                context.getMeeting().getVersion()
        );
    }

    /**
     * 统一通知出口，确保可观测字段一致。
     */
    private void publishMeetingNotice(MeetingSessionEntity meeting,
                                      String noticeType,
                                      String content,
                                      String clientRequestId,
                                      String traceId,
                                      Integer version) {
        Map<String, Object> extData = new LinkedHashMap<>();
        extData.put("noticeType", noticeType);
        extData.put("meetingId", meeting.getMeetingId());
        extData.put("roomId", meeting.getRoomId());
        extData.put("meetingStatus", meeting.getStatus() == null ? null : meeting.getStatus().getCode());
        extData.put("version", version);
        extData.put("clientRequestId", clientRequestId);
        extData.put("traceId", traceId);
        roomChatService.publishSystemNotice(meeting.getRoomId(), noticeType, content, JSON.toJSONString(extData));
    }

    private boolean isIdempotentHandled(String meetingId, String clientRequestId, String traceId) {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return false;
        }
        MeetingActionEntity existed = meetingRepository.queryActionByClientRequestId(meetingId, clientRequestId);
        if (existed == null) {
            return false;
        }
        log.info("【MEETING_ACTION_IDEMPOTENT_HIT】meetingId={}, clientRequestId={}, traceId={}, existedActionId={}, existedVersion={}",
                meetingId, clientRequestId, traceId, existed.getActionId(), existed.getMeetingVersion());
        return true;
    }

    private MeetingIssueEntity buildIssueIfNeeded(MeetingSessionEntity meeting,
                                                  MeetingActionType actionType,
                                                  String actorRole,
                                                  String targetRole,
                                                  String reason,
                                                  String requiredFix,
                                                  String deadline) {
        if (!MeetingActionType.RETURN.equals(actionType)) {
            return null;
        }
        return MeetingIssueEntity.builder()
                .issueId(buildIssueId())
                .meetingId(meeting.getMeetingId())
                .roomId(meeting.getRoomId())
                .reporterRole(actorRole)
                .assigneeRole(targetRole)
                .reason(normalizeText(reason, DEFAULT_RETURN_REASON))
                .requiredFix(normalizeText(requiredFix, DEFAULT_REQUIRED_FIX))
                .dueAt(parseDeadline(deadline))
                .status(ISSUE_STATUS_OPEN)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private MeetingSummaryVO buildSummaryIfNeeded(MeetingActionType actionType,
                                                  String actorRole,
                                                  String payload,
                                                  String reason,
                                                  String requiredFix) {
        if (!(MeetingActionType.APPROVE.equals(actionType) || MeetingActionType.CONCLUDE.equals(actionType))) {
            return null;
        }

        JSONObject payloadJson = parsePayload(payload);
        String conclusion = payloadJson == null ? null : payloadJson.getString("conclusion");
        String nextRoundCondition = payloadJson == null ? null : payloadJson.getString("nextRoundCondition");
        List<String> actionItems = extractActionItems(payloadJson, requiredFix);

        if (conclusion == null || conclusion.isBlank()) {
            conclusion = normalizeText(reason, "会议阶段结论已达成");
        }
        return MeetingSummaryVO.builder()
                .conclusion(conclusion)
                .actionItems(actionItems)
                .nextRoundCondition(nextRoundCondition)
                .concludedByRole(actorRole)
                .concludedAt(LocalDateTime.now())
                .build();
    }

    private List<String> extractActionItems(JSONObject payloadJson, String requiredFix) {
        if (payloadJson != null && payloadJson.containsKey("actionItems")) {
            List<String> actionItems = payloadJson.getList("actionItems", String.class);
            if (actionItems != null && !actionItems.isEmpty()) {
                return actionItems;
            }
        }
        if (requiredFix == null || requiredFix.isBlank()) {
            return new ArrayList<>();
        }
        return List.of(requiredFix);
    }

    private MeetingActionType parseActionType(String actionType) {
        MeetingActionType meetingActionType = MeetingActionType.getByCode(actionType);
        if (meetingActionType == null || MeetingActionType.START.equals(meetingActionType)) {
            throw new MissingException("不支持的会议动作类型");
        }
        return meetingActionType;
    }

    private void requireExpectedVersion(Integer expectedVersion) {
        if (expectedVersion == null) {
            throw new MissingException("expectedVersion 不能为空");
        }
    }

    private void validateCreateCommand(String roomId, String creatorId) {
        if (roomId == null || roomId.isBlank()) {
            throw new MissingException("roomId 不能为空");
        }
        if (creatorId == null || creatorId.isBlank()) {
            throw new MissingException("creatorId 不能为空");
        }
        ensureRoomExists(roomId);
    }

    private void ensureNoActiveMeeting(String roomId) {
        MeetingSessionEntity activeMeeting = meetingRepository.queryActiveMeetingByRoomId(roomId);
        if (activeMeeting != null) {
            throw new DependencyConflictException("当前房间存在进行中的会议，请先结束再创建新会议");
        }
    }

    private MeetingSessionEntity buildInitialMeeting(String meetingId,
                                                     String roomId,
                                                     String topic,
                                                     String goal,
                                                     String creatorId,
                                                     String creatorRole,
                                                     String pmId,
                                                     Map<String, String> roleAssignments,
                                                     LocalDateTime now) {
        String normalizedCreatorRole = normalizeRole(creatorRole, "boss");
        String resolvedPmId = (pmId == null || pmId.isBlank()) ? creatorId : pmId;
        Map<String, String> assignment = normalizeAssignments(roleAssignments, creatorId, normalizedCreatorRole, resolvedPmId);

        return MeetingSessionEntity.builder()
                .meetingId(meetingId)
                .roomId(roomId)
                .topic(normalizeText(topic, DEFAULT_MEETING_TOPIC))
                .goal(normalizeText(goal, DEFAULT_MEETING_GOAL))
                .status(MeetingStatus.PENDING)
                .roundNumber(0)
                .ownerId(creatorId)
                .ownerRole(normalizedCreatorRole)
                .pmId(resolvedPmId)
                .roleAssignments(assignment)
                .version(0)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private void ensureRoomExists(String roomId) {
        AiChatRoomEntity room = chatRoomRepository.queryRoomById(roomId);
        if (room == null) {
            throw new MissingException("房间不存在");
        }
    }

    private MeetingSessionEntity getRequiredMeeting(String roomId, String meetingId) {
        ensureRoomExists(roomId);
        MeetingSessionEntity meeting = resolveMeeting(roomId, meetingId);
        if (meeting == null) {
            throw new MissingException("会议不存在");
        }
        if (!Objects.equals(roomId, meeting.getRoomId())) {
            throw new DependencyConflictException("会议与房间不匹配");
        }
        return meeting;
    }

    private MeetingSessionEntity resolveMeeting(String roomId, String meetingId) {
        if (meetingId != null && !meetingId.isBlank()) {
            return meetingRepository.queryMeetingById(meetingId);
        }
        return meetingRepository.queryActiveMeetingByRoomId(roomId);
    }

    private void ensureStartState(MeetingSessionEntity meeting) {
        if (!MeetingStatus.PENDING.equals(meeting.getStatus())) {
            throw new DependencyConflictException("当前会议不处于待开始状态");
        }
    }

    private void ensureActionState(MeetingSessionEntity meeting, MeetingActionType actionType) {
        if (meeting.isTerminal()) {
            throw new DependencyConflictException("会议已结束，不能执行新动作");
        }

        MeetingStatus status = meeting.getStatus();
        switch (actionType) {
            case REPORT, QUESTION, ASSIGN -> ensureInStatus(status, MeetingStatus.IN_PROGRESS, MeetingStatus.RETURNED, MeetingStatus.WAIT_DECISION);
            case RETURN -> ensureInStatus(status, MeetingStatus.IN_PROGRESS, MeetingStatus.WAIT_DECISION);
            case DECISION -> ensureInStatus(status, MeetingStatus.IN_PROGRESS, MeetingStatus.RETURNED);
            case RESUME -> ensureInStatus(status, MeetingStatus.RETURNED, MeetingStatus.WAIT_DECISION, MeetingStatus.NEXT_ROUND_PENDING);
            case APPROVE, CONCLUDE -> ensureInStatus(status, MeetingStatus.IN_PROGRESS, MeetingStatus.RETURNED);
            case TERMINATE -> {
                if (MeetingStatus.TERMINATED.equals(status)) {
                    throw new DependencyConflictException("会议已终止");
                }
            }
            default -> throw new MissingException("不支持的动作类型");
        }
    }

    private void ensureInStatus(MeetingStatus current, MeetingStatus... expectedStatuses) {
        for (MeetingStatus expected : expectedStatuses) {
            if (expected.equals(current)) {
                return;
            }
        }
        throw new DependencyConflictException("当前状态不支持该动作");
    }

    private MeetingStatus applyTransition(MeetingSessionEntity meeting, MeetingActionType actionType) {
        return switch (actionType) {
            case RETURN -> MeetingStatus.RETURNED;
            case DECISION -> MeetingStatus.WAIT_DECISION;
            case RESUME -> MeetingStatus.IN_PROGRESS;
            case APPROVE -> MeetingStatus.CONCLUDED;
            case CONCLUDE -> MeetingStatus.NEXT_ROUND_PENDING;
            case TERMINATE -> MeetingStatus.TERMINATED;
            case REPORT, QUESTION, ASSIGN -> MeetingStatus.IN_PROGRESS;
            default -> meeting.getStatus();
        };
    }

    /**
     * M3: 轮次推进规则。
     * 规则：
     * 1) 开始会议时轮次=1（已在 startMeeting 中处理）
     * 2) 从 NEXT_ROUND_PENDING 恢复到 IN_PROGRESS 时，轮次+1
     */
    private void applyRoundAdvancePolicy(MeetingSessionEntity meeting,
                                         MeetingStatus beforeStatus,
                                         MeetingActionType actionType) {
        if (!MeetingActionType.RESUME.equals(actionType)) {
            return;
        }
        if (!MeetingStatus.NEXT_ROUND_PENDING.equals(beforeStatus)) {
            return;
        }

        Integer currentRound = meeting.getRoundNumber();
        if (currentRound == null || currentRound < 1) {
            meeting.setRoundNumber(1);
            return;
        }
        meeting.setRoundNumber(currentRound + 1);
    }

    /**
     * M2: 退回闭环目标角色强校验。
     * RETURN/RESUME 必须携带 targetRole，避免问题单失配和误关。
     */
    private void ensureTargetRoleRequired(MeetingActionType actionType, String targetRole) {
        if (!(MeetingActionType.RETURN.equals(actionType) || MeetingActionType.RESUME.equals(actionType))) {
            return;
        }
        if (targetRole == null || targetRole.isBlank()) {
            throw new MissingException("RETURN/RESUME 必须指定 targetRole");
        }
    }

    private void ensureActionPermission(MeetingSessionEntity meeting,
                                        MeetingActionType actionType,
                                        String actorId,
                                        String actorRole) {
        if (actorRole == null || actorRole.isBlank()) {
            throw new AuthException("缺少执行角色");
        }

        String role = actorRole.toLowerCase(Locale.ROOT);
        if ("boss".equals(role)) {
            return;
        }

        if (!matchesAssignment(meeting, role, actorId)) {
            throw new AuthException("当前执行人不在会议角色绑定中");
        }

        boolean allowed = switch (actionType) {
            case START, APPROVE, QUESTION, ASSIGN, DECISION, CONCLUDE, TERMINATE -> "pm".equals(role);
            case RETURN -> "pm".equals(role) || "architect".equals(role);
            case REPORT -> true;
            case RESUME -> "pm".equals(role) || "product".equals(role);
            default -> false;
        };
        if (!allowed) {
            throw new AuthException("当前角色无权执行该动作");
        }
    }

    private boolean matchesAssignment(MeetingSessionEntity meeting, String role, String actorId) {
        if (meeting.getRoleAssignments() == null || meeting.getRoleAssignments().isEmpty()) {
            return true;
        }
        String bindingMemberId = meeting.getRoleAssignments().get(role);
        if (bindingMemberId == null || bindingMemberId.isBlank()) {
            return true;
        }
        if (actorId == null || actorId.isBlank()) {
            return false;
        }
        return bindingMemberId.equals(actorId);
    }

    private List<MeetingIssueEntity> filterOpenIssues(List<MeetingIssueEntity> issues) {
        if (issues == null || issues.isEmpty()) {
            return new ArrayList<>();
        }
        List<MeetingIssueEntity> result = new ArrayList<>();
        for (MeetingIssueEntity issue : issues) {
            if (issue != null && ISSUE_STATUS_OPEN.equalsIgnoreCase(issue.getStatus())) {
                result.add(issue);
            }
        }
        return result;
    }

    private Map<String, String> normalizeAssignments(Map<String, String> roleAssignments,
                                                     String creatorId,
                                                     String creatorRole,
                                                     String pmId) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (roleAssignments != null) {
            for (Map.Entry<String, String> entry : roleAssignments.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                normalized.put(entry.getKey().trim().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }
        if (creatorRole != null && !creatorRole.isBlank()) {
            normalized.putIfAbsent(creatorRole.toLowerCase(Locale.ROOT), creatorId);
        }
        normalized.putIfAbsent("pm", pmId);
        return normalized;
    }

    private String buildMeetingId() {
        return "meeting_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String buildActionId() {
        return "ma_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String buildIssueId() {
        return "issue_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String normalizeRole(String role, String defaultRole) {
        if (role == null || role.isBlank()) {
            return defaultRole;
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * M3: 会议开始轮次规则。
     * 约束：开始会议后 roundNumber 必须 >= 1。
     */
    private Integer resolveStartRoundNumber(Integer currentRound) {
        if (currentRound == null || currentRound < 1) {
            return 1;
        }
        return currentRound;
    }

    private String normalizeText(String source, String defaultValue) {
        if (source == null || source.isBlank()) {
            return defaultValue;
        }
        return source.trim();
    }

    private LocalDateTime parseDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(deadline);
        } catch (DateTimeParseException parseException) {
            log.warn("【MEETING_DEADLINE_PARSE_WARN】deadline={}, reason=invalid-format", deadline);
            return null;
        }
    }

    private JSONObject parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(payload);
        } catch (Exception parseException) {
            log.warn("【MEETING_PAYLOAD_PARSE_WARN】payload={}, reason=invalid-json", payload);
            return null;
        }
    }

    /**
     * 动作执行临时上下文，避免方法间参数过长。
     */
    @lombok.Data
    @lombok.Builder
    private static class ActionApplyContext {
        private MeetingSessionEntity meeting;
        private MeetingActionType actionType;
        private String actorId;
        private String actorRole;
        private String targetRole;
        private MeetingStatus beforeStatus;
        private MeetingStatus afterStatus;
        private MeetingSummaryVO summary;
        private MeetingIssueEntity issue;
        private MeetingActionEntity action;
        private String clientRequestId;
        private String traceId;
    }
}
