package com.dasi.domain.room.service.room.impl;

import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import org.springframework.stereotype.Service;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.room.impl
 * @Author: xerina
 * @CreateTime: 2026-03-25  17:51
 * @Description: 智能体成员处理策略
 */
@Service("AGENT_MEMBER")
public class AgentMemberService extends AbstractRoomMemberService {

    @Override
    public String queryMemberName(String memberId, String memberType) {
       return aiRepository.queryAgentNameByAgentId(memberId);
    }

    @Override
    protected boolean doJoin(AiChatRoomMemberEntity memberEntity) {
        // 智能体入场 (未来可扩展其特有的执行链配置)
        return true;
    }
}
