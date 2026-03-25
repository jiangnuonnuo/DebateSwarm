package com.dasi.domain.room.service.room.impl;

import com.dasi.domain.ai.model.vo.AiClientVO;
import com.dasi.domain.ai.repository.IAiRepository;
import com.dasi.domain.room.service.room.IRoomMemberInsetService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.room.impl
 * @Author: xerina
 * @CreateTime: 2026-03-25  17:58
 * @Description: TODO
 */
@Service("CLIENT_MEMBER")
public class ClientMemberService implements IRoomMemberInsetService {

    @Resource
    private IAiRepository aiRepository;

    @Override
    public String queryMemberName(String memberId, String memberType) {
        AiClientVO clientVO = aiRepository.queryAiClientVO(memberId);
        return clientVO != null ? clientVO.getClientName() : "未知客户端";
    }

}
