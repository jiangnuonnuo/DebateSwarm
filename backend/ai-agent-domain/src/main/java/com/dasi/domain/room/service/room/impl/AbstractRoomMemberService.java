package com.dasi.domain.room.service.room.impl;

import com.alibaba.fastjson2.JSON;
import com.dasi.domain.ai.repository.IAiRepository;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.service.IRoomChatService;
import com.dasi.domain.room.service.room.IRoomMemberInsetService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
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

    @Resource
    protected IRoomChatService roomChatService;

    @Override
    public boolean joinRoom(AiChatRoomMemberEntity memberEntity) {
        // 1. 基础加入逻辑 (basicJoin)
        if (!basicJoin(memberEntity)) {
            return false;
        }

        // 2. 特色加入逻辑 (钩子方法)
        boolean joined = doJoin(memberEntity);
        if (joined && "CLIENT".equals(memberEntity.getMemberType())) {
            runAfterCommit(() -> publishMemberJoinNotice(memberEntity));
        }
        return joined;
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

    /**
     * MEMBER_JOIN 属于成员入场后的房间动态，不参与规则树调度。
     * 放在共享加入主链统一发布，能保证所有 CLIENT 入房都走同一条基础设施通知路径。
     */
    protected void publishMemberJoinNotice(AiChatRoomMemberEntity memberEntity) {
        LinkedHashMap<String, Object> extData = new LinkedHashMap<>();
        extData.put("noticeType", "MEMBER_JOIN");
        extData.put("memberId", memberEntity.getMemberId());
        extData.put("memberName", memberEntity.getMemberName());
        extData.put("memberType", memberEntity.getMemberType());
        roomChatService.publishSystemNotice(
                memberEntity.getRoomId(),
                "MEMBER_JOIN",
                String.format("新成员入场：%s（%s）加入群聊，请各位小伙伴开始互动！",
                        memberEntity.getMemberName(), memberEntity.getMemberId()),
                JSON.toJSONString(extData)
        );
    }

    protected void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

}
