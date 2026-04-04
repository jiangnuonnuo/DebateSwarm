package com.dasi.domain.room.model.valobj;

import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    private List<DebateMemberStatusVO> proMembers;

    private List<DebateMemberStatusVO> conMembers;

    private List<DebateTurnRecordVO> roundHistory;

    private List<AiChatRoomMessageEntity> recentConversation;
}
