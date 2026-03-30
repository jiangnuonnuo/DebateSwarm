package com.dasi.infrastructure.repository;

import com.alibaba.fastjson2.JSON;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.infrastructure.persistent.dao.IAiChatRoomDao;
import com.dasi.infrastructure.persistent.dao.IAiChatRoomMemberDao;
import com.dasi.infrastructure.persistent.dao.IAiChatRoomMessageDao;
import com.dasi.infrastructure.persistent.po.AiChatRoom;
import com.dasi.infrastructure.persistent.po.AiChatRoomMember;
import com.dasi.infrastructure.persistent.po.AiChatRoomMessage;
import com.dasi.infrastructure.util.RedisUtil;
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
 * @Description: 聊天室仓储实现类 (接入元数据缓存)
 */
@Repository
public class ChatRoomRepository extends AbstractRepository implements IChatRoomRepository {

    @Resource
    private IAiChatRoomDao aiChatRoomDao;

    @Resource
    private IAiChatRoomMemberDao aiChatRoomMemberDao;

    @Resource
    private IAiChatRoomMessageDao aiChatRoomMessageDao;

    @Resource
    private RedisUtil redisUtil;

    private static final String ROOM_META_KEY = "room_meta:";
    private static final String MEMBER_NAME_KEY = "member_name:";
    private static final String ROOM_CLIENTS_KEY = "room_clients:";

    @Override
    public void saveRoom(AiChatRoomEntity roomEntity) {
        // 1. 领域对象转换为持久化对象 PO
        AiChatRoom po = AiChatRoom.builder()
                .roomId(roomEntity.getRoomId())
                .roomName(roomEntity.getRoomName())
                .roomDesc(roomEntity.getRoomDesc())
                .ownerId(roomEntity.getOwnerId())
                .roomType(roomEntity.getRoomType())
                .extConfig(roomEntity.getExtConfig())
                .status(roomEntity.getStatus())
                .build();

        // 2. 判断是插入还是更新
        AiChatRoom existing = aiChatRoomDao.queryRoomByRoomId(roomEntity.getRoomId());
        if (existing == null) {
            aiChatRoomDao.insert(po);
        } else {
            aiChatRoomDao.updateRoomInfo(po);
        }
        // 3. 清理缓存
        redisUtil.deleteByKey(ROOM_META_KEY + roomEntity.getRoomId());
    }

    @Override
    public void deleteRoom(String roomId) {
        aiChatRoomDao.deleteByRoomId(roomId);
        redisUtil.deleteByKey(ROOM_META_KEY + roomId);
    }

    @Override
    public AiChatRoomEntity queryRoomById(String roomId) {
        String cacheKey = ROOM_META_KEY + roomId;
        return getFromCacheOrDb(cacheKey, AiChatRoomEntity.class, () -> {
            AiChatRoom po = aiChatRoomDao.queryRoomByRoomId(roomId);
            if (po == null) return null;
            return AiChatRoomEntity.builder()
                    .roomId(po.getRoomId())
                    .roomName(po.getRoomName())
                    .roomDesc(po.getRoomDesc())
                    .ownerId(po.getOwnerId())
                    .roomType(po.getRoomType())
                    .extConfig(po.getExtConfig())
                    .status(po.getStatus())
                    .createTime(po.getCreateTime())
                    .updateTime(po.getUpdateTime())
                    .build();
        });
    }

    @Override
    public List<AiChatRoomEntity> queryRoomsByOwnerId(Long ownerId) {
        List<AiChatRoom> pos = aiChatRoomDao.queryRoomListByOwnerId(ownerId);
        if (pos == null || pos.isEmpty()) {
            return new ArrayList<>();
        }
        return pos.stream().map(po -> AiChatRoomEntity.builder()
                .roomId(po.getRoomId())
                .roomName(po.getRoomName())
                .roomDesc(po.getRoomDesc())
                .ownerId(po.getOwnerId())
                .roomType(po.getRoomType())
                .extConfig(po.getExtConfig())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<AiChatRoomEntity> queryRoomsByMemberId(String memberId) {
        List<AiChatRoom> pos = aiChatRoomDao.queryRoomListByMemberId(memberId);
        if (pos == null || pos.isEmpty()) {
            return new ArrayList<>();
        }
        return pos.stream().map(po -> AiChatRoomEntity.builder()
                .roomId(po.getRoomId())
                .roomName(po.getRoomName())
                .roomDesc(po.getRoomDesc())
                .ownerId(po.getOwnerId())
                .roomType(po.getRoomType())
                .extConfig(po.getExtConfig())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build()).collect(Collectors.toList());
    }

    @Override
    public void saveMember(AiChatRoomMemberEntity memberEntity) {
        AiChatRoomMember po = AiChatRoomMember.builder()
                .roomId(memberEntity.getRoomId())
                .memberId(memberEntity.getMemberId())
                .memberType(memberEntity.getMemberType())
                .memberName(memberEntity.getMemberName())
                .agentSessionId(memberEntity.getAgentSessionId())
                .build();
        aiChatRoomMemberDao.insert(po);
        // 清理成员相关缓存
        redisUtil.deleteByKey(MEMBER_NAME_KEY + memberEntity.getRoomId() + ":" + memberEntity.getMemberId());
        redisUtil.deleteByKey(ROOM_CLIENTS_KEY + memberEntity.getRoomId());
    }

    @Override
    public void deleteMember(String roomId, String memberId) {
        aiChatRoomMemberDao.deleteMember(roomId, memberId);
        redisUtil.deleteByKey(MEMBER_NAME_KEY + roomId + ":" + memberId);
        redisUtil.deleteByKey(ROOM_CLIENTS_KEY + roomId);
    }

    @Override
    public List<AiChatRoomMemberEntity> queryMembersByRoomId(String roomId) {
        List<AiChatRoomMember> pos = aiChatRoomMemberDao.queryMemberByRoomId(roomId);
        if (pos == null || pos.isEmpty()) {
            return new ArrayList<>();
        }
        return pos.stream().map(po -> AiChatRoomMemberEntity.builder()
                .roomId(po.getRoomId())
                .memberId(po.getMemberId())
                .memberType(po.getMemberType())
                .memberName(po.getMemberName())
                .agentSessionId(po.getAgentSessionId())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<AiChatRoomMemberEntity> queryAgentsByRoomId(String roomId) {
        List<AiChatRoomMember> pos = aiChatRoomMemberDao.queryAgentMemberByRoomId(roomId);
        if (pos == null || pos.isEmpty()) {
            return new ArrayList<>();
        }
        return pos.stream().map(po -> AiChatRoomMemberEntity.builder()
                .roomId(po.getRoomId())
                .memberId(po.getMemberId())
                .memberType(po.getMemberType())
                .memberName(po.getMemberName())
                .agentSessionId(po.getAgentSessionId())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<AiChatRoomMemberEntity> queryClientsByRoomId(String roomId) {
        String cacheKey = ROOM_CLIENTS_KEY + roomId;
        String json = redisUtil.getValue(cacheKey, String.class);
        if (json != null) {
            return JSON.parseArray(json, AiChatRoomMemberEntity.class);
        }
        
        List<AiChatRoomMember> pos = aiChatRoomMemberDao.queryClientMemberByRoomId(roomId);
        if (pos == null || pos.isEmpty()) return new ArrayList<>();
        
        List<AiChatRoomMemberEntity> result = pos.stream().map(po -> AiChatRoomMemberEntity.builder()
                .roomId(po.getRoomId())
                .memberId(po.getMemberId())
                .memberType(po.getMemberType())
                .memberName(po.getMemberName())
                .agentSessionId(po.getAgentSessionId())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build()).collect(Collectors.toList());
        
        redisUtil.setValue(cacheKey, JSON.toJSONString(result));
        return result;
    }

    @Override
    public String queryMemberName(String roomId, String memberId) {
        String cacheKey = MEMBER_NAME_KEY + roomId + ":" + memberId;
        return getFromCacheOrDb(cacheKey, String.class, () -> {
            AiChatRoomMember member = aiChatRoomMemberDao.queryMemberByRoomIdAndMemberId(roomId, memberId);
            return member != null ? member.getMemberName() : "未知成员";
        });
    }

    @Override
    public String queryRoomName(String roomId) {
        AiChatRoomEntity room = queryRoomById(roomId);
        return room != null ? room.getRoomName() : "未知聊天室";
    }

    @Override
    public void saveMessage(AiChatRoomMessageEntity messageEntity) {
        // 手动映射 Entity 到 PO
        AiChatRoomMessage po = AiChatRoomMessage.builder()
                .roomId(messageEntity.getRoomId())
                .messageId(messageEntity.getMessageId())
                .senderId(messageEntity.getSenderId())
                .senderName(messageEntity.getSenderName())
                .senderType(messageEntity.getSenderType())
                .messageRole(messageEntity.getMessageRole())
                .content(messageEntity.getContent())
                .atMemberId(messageEntity.getAtMemberId())
                .isPreempted(messageEntity.getIsPreempted())
                .build();
        aiChatRoomMessageDao.insert(po);
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

    @Override
    public List<AiChatRoomMessageEntity> queryMessagesByCursor(String roomId, Long cursorTime, Integer limit) {
        List<AiChatRoomMessage> pos = aiChatRoomMessageDao.queryMessagesByCursor(roomId, cursorTime, limit);
        if (pos == null || pos.isEmpty()) {
            return new ArrayList<>();
        }
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

    @Override
    public String queryExtConfigByRoomId(String roomId) {
        return aiChatRoomDao.queryExtConfigByRoomId(roomId) ;
    }

    @Override
    public Boolean queryMemberExistByMemberId(String roomId, String memberId) {
        AiChatRoomMember aiChatRoomMember = aiChatRoomMemberDao.queryMemberByRoomIdAndMemberId(roomId, memberId);
        return aiChatRoomMember != null ? true : false;
    }
}
