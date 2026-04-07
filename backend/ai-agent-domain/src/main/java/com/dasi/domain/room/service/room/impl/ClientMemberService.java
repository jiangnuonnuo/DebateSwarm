package com.dasi.domain.room.service.room.impl;

import com.dasi.domain.ai.model.enumeration.AiArmoryType;
import com.dasi.domain.ai.model.vo.AiClientVO;
import com.dasi.domain.ai.service.dispatch.IDispatchService;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.room.impl
 * @Author: xerina
 * @CreateTime: 2026-03-25  17:58
 * @Description: 客户端成员处理策略
 */
@Slf4j
@Service("CLIENT_MEMBER")
public class ClientMemberService extends AbstractRoomMemberService {

    @Resource
    private IDispatchService dispatchService;

    @Override
    public String queryMemberName(String memberId, String memberType) {
        AiClientVO clientVO = aiRepository.queryAiClientVO(memberId);
        return clientVO != null ? clientVO.getClientName() : "未知客户端";
    }

    @Override
    protected boolean doJoin(AiChatRoomMemberEntity memberEntity) {
        // 客户端入场后只做静态能力装配预热，禁止写回 ai_prompt，避免房间动态污染原始人设。
        String clientId = memberEntity.getMemberId();
        dispatchService.dispatchArmoryStrategy(AiArmoryType.ARMORY_CHAT.getType(), Collections.singleton(clientId));
        log.info("【PROMPT_PERSIST_BLOCKED】CLIENT 入房仅执行静态装配，不再改写提示词入库 roomId={}, clientId={}",
                memberEntity.getRoomId(), clientId);
        return true;
    }
}
