package com.dasi.trigger.controller;

import com.dasi.api.IChatRoomService;
import com.dasi.api.dto.request.ArbitratorSetRequest;
import com.dasi.api.dto.request.ChatRoomCreateRequest;
import com.dasi.api.dto.request.ChatRoomCursorRequest;
import com.dasi.api.dto.request.ChatRoomMemberRequest;
import com.dasi.api.dto.request.DebateNextRoundRequest;
import com.dasi.api.dto.request.DebateRoomRequest;
import com.dasi.api.dto.request.DebateStartRequest;
import com.dasi.api.dto.request.DebateWinnerRequest;
import com.dasi.api.dto.response.ChatRoomDTO;
import com.dasi.api.dto.response.ChatRoomMemberDTO;
import com.dasi.api.dto.response.ChatRoomMessageDTO;
import com.dasi.api.dto.response.DebateMemberDTO;
import com.dasi.api.dto.response.DebateRoundSummaryDTO;
import com.dasi.api.dto.response.DebateStatusDTO;
import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.valobj.DebateMemberStatusVO;
import com.dasi.domain.room.model.valobj.DebateRoundSummaryVO;
import com.dasi.domain.room.model.valobj.DebateStatusVO;
import com.dasi.domain.room.model.valobj.DebateRoundConfigVO;
import com.dasi.domain.room.service.IRoomAdminService;
import com.dasi.domain.room.service.debate.IDebateService;
import com.dasi.domain.util.jwt.UserContext;
import com.dasi.types.result.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.trigger.controller
 * @Author: xerina
 * @CreateTime: 2026-03-24  17:52
 * @Description: 聊天室管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/chat-room")
public class ChatRoomController implements IChatRoomService {

    @Resource
    private IRoomAdminService roomAdminService;

    @Resource
    private UserContext userContext;

    @Resource
    private IDebateService debateService;

    @Override
    @PostMapping("/create")
    public Result<String> createRoom(@RequestBody ChatRoomCreateRequest request) {
        try {
            Long userId = userContext.getUserId();
            AiChatRoomEntity roomEntity = AiChatRoomEntity.builder()
                    .roomName(request.getRoomName())
                    .roomDesc(request.getRoomDesc())
                    .roomType(request.getRoomType())
                    .ownerId(userId)
                    .build();

            String roomId = roomAdminService.createRoom(roomEntity);
            
            // 创建者默认加入房间
            roomAdminService.joinRoom(AiChatRoomMemberEntity.builder()
                    .roomId(roomId)
                    .memberId(userContext.getUserName())
                    .memberType("USER")
                    .memberName(userContext.getUserName())
                    .build());

            return Result.success(roomId);
        } catch (Exception e) {
            log.error("【房间管理】创建失败", e);
            return Result.error("创建聊天室失败");
        }
    }

    @Override
    @DeleteMapping("/delete")
    public Result<Boolean> deleteRoom(@RequestParam("roomId") String roomId) {
        try {
            // TODO: 校验当前用户是否有权删除 (例如是否为 Owner)
            boolean success = roomAdminService.deleteRoom(roomId);
            return Result.success(success);
        } catch (Exception e) {
            log.error("【房间管理】删除失败 roomId={}", roomId, e);
            return Result.error("删除聊天室失败");
        }
    }

    @Override
    @PostMapping("/member/add")
    public Result<Boolean> addMember(@RequestBody ChatRoomMemberRequest request) {
        try {
            AiChatRoomMemberEntity memberEntity = AiChatRoomMemberEntity.builder()
                    .roomId(request.getRoomId())
                    .memberId(request.getMemberId())
                    .memberType(request.getMemberType())
                    .build();
            boolean success = roomAdminService.joinRoom(memberEntity);
            return Result.success(success);
        } catch (Exception e) {
            log.error("【房间管理】添加成员失败", e);
            return Result.error("添加成员失败");
        }
    }

    @Override
    @PostMapping("/member/remove")
    public Result<Boolean> removeMember(@RequestBody ChatRoomMemberRequest request) {
        try {
            boolean success = roomAdminService.leaveRoom(request.getRoomId(), request.getMemberId());
            return Result.success(success);
        } catch (Exception e) {
            log.error("【房间管理】移除成员失败", e);
            return Result.error("移除成员失败");
        }
    }

    @Override
    @GetMapping("/member/list")
    public Result<List<ChatRoomMemberDTO>> queryMemberList(@RequestParam("roomId") String roomId) {
        try {
            List<AiChatRoomMemberEntity> members = roomAdminService.queryMembersInRoom(roomId);
            List<ChatRoomMemberDTO> vos = members.stream().map(entity -> ChatRoomMemberDTO.builder()
                    .memberId(entity.getMemberId())
                    .memberName(entity.getMemberName())
                    .memberType(entity.getMemberType())
                    .build()).collect(Collectors.toList());
            return Result.success(vos);
        } catch (Exception e) {
            log.error("【房间管理】查询成员列表失败 roomId={}", roomId, e);
            return Result.error("获取成员列表失败");
        }
    }

    @Override
    @GetMapping("/list")
    public Result<List<ChatRoomDTO>> queryRoomList() {
        try {
            String userName = userContext.getUserName();
            List<AiChatRoomEntity> rooms = roomAdminService.queryRoomsByMemberId(userName);
            
            List<ChatRoomDTO> vos = rooms.stream().map(entity -> ChatRoomDTO.builder()
                    .roomId(entity.getRoomId())
                    .roomName(entity.getRoomName())
                    .roomDesc(entity.getRoomDesc())
                    .roomType(entity.getRoomType())
                    .ownerId(entity.getOwnerId())
                    .status(entity.getStatus())
                    .createTime(Date.from(entity.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()))
                    .build()).collect(Collectors.toList());
            
            return Result.success(vos);
        } catch (Exception e) {
            log.error("【房间管理】查询列表失败", e);
            return Result.error("获取房间列表失败");
        }
    }

    @Override
    @PostMapping("/message/cursor")
    public Result<List<ChatRoomMessageDTO>> queryMessagesByCursor(@RequestBody ChatRoomCursorRequest request) {
        try {
            List<AiChatRoomMessageEntity> messages = roomAdminService.queryMessagesByCursor(
                    request.getRoomId(), 
                    request.getCursorTime(), 
                    request.getPageSize()
            );

            List<ChatRoomMessageDTO> vos = messages.stream().map(entity -> ChatRoomMessageDTO.builder()
                    .messageId(entity.getMessageId())
                    .senderId(entity.getSenderId())
                    .senderName(entity.getSenderName())
                    .senderType(entity.getSenderType())
                    .messageRole(entity.getMessageRole())
                    .content(entity.getContent())
                    .extData(entity.getExtData())
                    .isPreempted(entity.getIsPreempted())
                    .createTime(Date.from(entity.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()))
                    .timestamp(entity.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    .build()).collect(Collectors.toList());

            return Result.success(vos);
        } catch (Exception e) {
            log.error("【房间管理】查询历史记录失败", e);
            return Result.error("获取聊天记录失败");
        }
    }

    @Override
    @PostMapping("/arbitrator/set")
    public Result<Boolean> setArbitrator(@RequestBody ArbitratorSetRequest request) {
        try {
            debateService.setArbitrator(request.getRoomId(), request.getClientId());
            return Result.success(Boolean.TRUE);
        } catch (Exception e) {
            log.error("【辩论管理】设置仲裁者失败", e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    @DeleteMapping("/arbitrator/remove")
    public Result<Boolean> removeArbitrator(@RequestParam("roomId") String roomId) {
        try {
            debateService.removeArbitrator(roomId);
            return Result.success(Boolean.TRUE);
        } catch (Exception e) {
            log.error("【辩论管理】移除仲裁者失败", e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    @PostMapping("/debate/start")
    public Result<String> startDebate(@RequestBody DebateStartRequest request) {
        try {
            return Result.success(debateService.startDebate(
                    request.getRoomId(),
                    request.getTopic(),
                    request.getProClientIds(),
                    request.getConClientIds(),
                    request.getTurnsPerRound()
            ));
        } catch (Exception e) {
            log.error("【辩论管理】开始辩论失败", e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    @PostMapping("/debate/round/winner")
    public Result<Boolean> declareRoundWinner(@RequestBody DebateWinnerRequest request) {
        try {
            debateService.declareRoundWinner(request.getRoomId(), request.getWinnerSide());
            return Result.success(Boolean.TRUE);
        } catch (Exception e) {
            log.error("【辩论管理】宣布胜方失败", e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    @PostMapping("/debate/round/next")
    public Result<Boolean> startNextRound(@RequestBody DebateNextRoundRequest request) {
        try {
            debateService.startNextRound(
                    request.getRoomId(),
                    DebateRoundConfigVO.builder()
                            .proClientIds(request.getProClientIds())
                            .conClientIds(request.getConClientIds())
                            .turnsPerRound(request.getTurnsPerRound())
                            .build()
            );
            return Result.success(Boolean.TRUE);
        } catch (Exception e) {
            log.error("【辩论管理】开始下一轮失败", e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    @PostMapping("/debate/stop")
    public Result<Boolean> stopDebate(@RequestBody DebateRoomRequest request) {
        try {
            debateService.stopDebate(request.getRoomId());
            return Result.success(Boolean.TRUE);
        } catch (Exception e) {
            log.error("【辩论管理】停止辩论失败", e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    @GetMapping("/debate/status")
    public Result<DebateStatusDTO> queryDebateStatus(@RequestParam("roomId") String roomId) {
        try {
            return Result.success(convertDebateStatus(debateService.queryDebateStatus(roomId)));
        } catch (Exception e) {
            log.error("【辩论管理】查询辩论状态失败", e);
            return Result.error(e.getMessage());
        }
    }

    private DebateStatusDTO convertDebateStatus(DebateStatusVO statusVO) {
        if (statusVO == null) {
            return null;
        }
        return DebateStatusDTO.builder()
                .sessionId(statusVO.getSessionId())
                .topic(statusVO.getTopic())
                .status(statusVO.getStatus() == null ? null : statusVO.getStatus().getCode())
                .currentRound(statusVO.getCurrentRound())
                .currentTurn(statusVO.getCurrentTurn())
                .turnsPerRound(statusVO.getTurnsPerRound())
                .roundWinners(statusVO.getRoundWinners())
                .arbitratorClientId(statusVO.getArbitratorClientId())
                .arbitratorName(statusVO.getArbitratorName())
                .waitingForWinner(statusVO.getWaitingForWinner())
                .pendingRoundNumber(statusVO.getPendingRoundNumber())
                .lastRoundSummary(convertRoundSummary(statusVO.getLastRoundSummary()))
                .proMembers(convertMembers(statusVO.getProMembers()))
                .conMembers(convertMembers(statusVO.getConMembers()))
                .canDeclareWinner(statusVO.getCanDeclareWinner())
                .canStartNextRound(statusVO.getCanStartNextRound())
                .canStopDebate(statusVO.getCanStopDebate())
                .build();
    }

    private DebateRoundSummaryDTO convertRoundSummary(DebateRoundSummaryVO summaryVO) {
        if (summaryVO == null) {
            return null;
        }
        return DebateRoundSummaryDTO.builder()
                .roundNumber(summaryVO.getRoundNumber())
                .turnCount(summaryVO.getTurnCount())
                .lastSpeakerId(summaryVO.getLastSpeakerId())
                .lastSpeakerName(summaryVO.getLastSpeakerName())
                .waitingForWinner(summaryVO.getWaitingForWinner())
                .build();
    }

    private List<DebateMemberDTO> convertMembers(List<DebateMemberStatusVO> members) {
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .map(item -> DebateMemberDTO.builder()
                        .clientId(item.getClientId())
                        .clientName(item.getClientName())
                        .build())
                .collect(Collectors.toList());
    }
}
