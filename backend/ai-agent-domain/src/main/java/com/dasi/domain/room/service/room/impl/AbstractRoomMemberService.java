package com.dasi.domain.room.service.room.impl;

import com.dasi.domain.ai.repository.IAiRepository;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.service.room.IRoomMemberInsetService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.room.impl
 * @Author: xerina
 * @CreateTime: 2026-03-31
 * @Description: 房间成员处理抽象基类 (基础加入逻辑)
 */
@Slf4j
public abstract class AbstractRoomMemberService implements IRoomMemberInsetService {

    @Resource
    protected IChatRoomRepository chatRoomRepository;

    @Resource
    protected IAiRepository aiRepository;

    @Override
    public boolean joinRoom(AiChatRoomMemberEntity memberEntity) {
        // 1. 基础加入逻辑 (basicJoin)
        if (!basicJoin(memberEntity)) {
            return false;
        }

        // 2. 特色加入逻辑 (钩子方法)
        return doJoin(memberEntity);
    }

    /**
     * 通用加入逻辑: 幂等性校验、名称获取、Session生成、持久化
     */
    protected boolean basicJoin(AiChatRoomMemberEntity memberEntity) {
        String roomId = memberEntity.getRoomId();
        String memberId = memberEntity.getMemberId();
        String memberType = memberEntity.getMemberType();

        // 1) 幂等性校验
        Boolean exist = chatRoomRepository.queryMemberExistByMemberId(roomId, memberId);
        if (Boolean.TRUE.equals(exist)) {
            log.warn("【房间管理】成员已存在，无需重复加入：roomId={}, memberId={}", roomId, memberId);
            return false;
        }

        // 2) 补全名称
        String memberName = queryMemberName(memberId, memberType);
        memberEntity.setMemberName(memberName);

        // 3) 生成 SessionID (针对 AI 类型)
        if (("AGENT".equals(memberType) || "CLIENT".equals(memberType)) && memberEntity.getAgentSessionId() == null) {
            memberEntity.setAgentSessionId("session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }

        // 4) 持久化
        chatRoomRepository.saveMember(memberEntity);
        log.info("【房间管理】成员基础加入完成：roomId={}, memberId={}, type={}, name={}", 
                roomId, memberId, memberType, memberName);
        return true;
    }

    /**
     * 子类特有的加入逻辑
     */
    protected abstract boolean doJoin(AiChatRoomMemberEntity memberEntity);

}
