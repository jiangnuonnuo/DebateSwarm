package com.dasi.infrastructure.repository;

import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.infrastructure.persistent.dao.IAiChatRoomDao;
import com.dasi.infrastructure.persistent.dao.IAiChatRoomMemberDao;
import com.dasi.infrastructure.persistent.dao.IAiChatRoomMessageDao;
import com.dasi.infrastructure.persistent.po.AiChatRoom;
import com.dasi.infrastructure.persistent.po.AiChatRoomMember;
import com.dasi.infrastructure.persistent.po.AiChatRoomMessage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.infrastructure.repository
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:08
 * @Description: 聊天室仓储实现类 (负责 PO 到 Entity 的转换)
 */
@Repository
public class ChatRoomRepository implements IChatRoomRepository {

    @Resource
    private IAiChatRoomDao aiChatRoomDao;

    @Resource
    private IAiChatRoomMemberDao aiChatRoomMemberDao;

    @Resource
    private IAiChatRoomMessageDao aiChatRoomMessageDao;

    @Override
    public String queryMemberName(String roomId, String memberId) {
        AiChatRoomMember member = aiChatRoomMemberDao.queryMemberByRoomIdAndMemberId(roomId, memberId);
        return member != null ? member.getMemberName() : "未知成员";
    }

    @Override
    public String queryRoomName(String roomId) {
        AiChatRoom room = aiChatRoomDao.queryRoomByRoomId(roomId);
        return room != null ? room.getRoomName() : "未知聊天室";
    }

    @Override
    public List<AiChatRoomMessageEntity> queryContextMessages(String roomId, Integer limit) {
        // 1. 调用 DAO 查出基础设施层的 PO 列表
        List<AiChatRoomMessage> pos = aiChatRoomMessageDao.queryContextMessages(roomId, limit);
        if (pos == null || pos.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 核心职责：将 PO 转换为 Domain 层的 Entity
        return pos.stream().map(po -> AiChatRoomMessageEntity.builder()
                .roomId(po.getRoomId())
                .messageId(po.getMessageId())
                .senderId(po.getSenderId())
                .senderName(po.getSenderName())
                .senderType(po.getSenderType())
                .messageRole(po.getMessageRole())
                .content(po.getContent())
                .isPreempted(po.getIsPreempted())
                .createTime(po.getCreateTime())
                .build()).collect(Collectors.toList());
    }
}
