package com.dasi.domain.room.model.valobj;

import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @Author: xerina
 * @Description: 仲裁者Prompt上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArbitrationPromptContextVO {

    private String roomId;

    private String sessionId;

    private String topic;

    private String arbitratorClientId;

    private Integer currentRound;

    private Integer currentTurn;

    private Integer turnsPerRound;

    private String lastSpeakerClientId;

    private String lastSpeakerName;

    /** 本轮可选的发言者 clientId 列表 */
    private List<String> candidateSpeakerIds;

    /** 在当前候选集中更推荐优先尝试的 speakerId 顺序 */
    private List<String> preferredSpeakerIds;

    /** 候选人入房顺序，值越小表示越早进入房间 */
    private Map<String, Integer> candidateJoinOrder;

    /** 上一位发言者阵营 */
    private String lastSpeakerSide;

    /** 当前轮每位辩手的已发言次数 */
    private Map<String, Integer> speakerHistoryStats;

    /** 运行时渲染后的仲裁指令文本，不入库 */
    private String debateInstruction;

    /** 当前槽位强制要求的阵营；为空表示首发或全体可选 */
    private String requiredSide;

    /** 当前槽位明确排除的 speakerId 集合 */
    private List<String> excludedSpeakerIds;

    /** 当前槽位的重试次数 */
    private Integer slotRetryCount;

    private List<DebateMemberStatusVO> proMembers;

    private List<DebateMemberStatusVO> conMembers;

    private List<DebateTurnRecordVO> roundHistory;

    private List<AiChatRoomMessageEntity> recentConversation;
}
