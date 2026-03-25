package com.dasi.domain.room.service.room.impl;

import com.dasi.domain.ai.repository.IAiRepository;
import com.dasi.domain.room.service.room.IRoomMemberInsetService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.room.impl
 * @Author: xerina
 * @CreateTime: 2026-03-25  17:51
 * @Description: TODO
 */

@Service("AGENT_MEMBER")
public class AgentMemberService implements IRoomMemberInsetService {

    @Resource
    private IAiRepository aiRepository;

    @Override
    public String queryMemberName(String memberId, String memberType) {
       return aiRepository.queryAgentNameByAgentId(memberId);
    }

}
