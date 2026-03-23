package com.dasi.domain.room.service.room;

import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.service.IRoomAdminService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.room
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:05
 * @Description: 房间管理服务实现类
 */
@Slf4j
@Service
public class RoomAdminService implements IRoomAdminService {

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Override
    public String createRoom(AiChatRoomEntity roomEntity) {
        // 1. 补全基础信息
        if (roomEntity.getRoomId() == null || roomEntity.getRoomId().isEmpty()) {
            roomEntity.setRoomId("room_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }
        if (roomEntity.getStatus() == null) {
            roomEntity.setStatus(0); 
        }
        if (roomEntity.getRoomType() == null) {
            roomEntity.setRoomType("GROUP");
        }

        log.info("【房间管理】开始创建房间：roomId={}, roomName={}", roomEntity.getRoomId(), roomEntity.getRoomName());
        chatRoomRepository.saveRoom(roomEntity);
        return roomEntity.getRoomId();
    }

    @Override
    public boolean updateRoom(AiChatRoomEntity roomEntity) {
        if (roomEntity.getRoomId() == null) {
            log.warn("【房间管理】更新房间失败：roomId 不能为空");
            return false;
        }
        log.info("【房间管理】更新房间信息：roomId={}", roomEntity.getRoomId());
        chatRoomRepository.saveRoom(roomEntity);
        return true;
    }

    @Override
    public boolean deleteRoom(String roomId) {
        log.info("【房间管理】逻辑删除房间：roomId={}", roomId);
        chatRoomRepository.deleteRoom(roomId);
        return true;
    }

    @Override
    public AiChatRoomEntity queryRoomById(String roomId) {
        return chatRoomRepository.queryRoomById(roomId);
    }

    @Override
    public List<AiChatRoomEntity> queryRoomsByOwnerId(Long ownerId) {
        return chatRoomRepository.queryRoomsByOwnerId(ownerId);
    }

    @Override
    public boolean joinRoom(AiChatRoomMemberEntity memberEntity) {
        if (memberEntity.getRoomId() == null || memberEntity.getMemberId() == null) {
            log.warn("【房间管理】加入房间失败：roomId 或 memberId 不能为空");
            return false;
        }
        // 如果是 Agent，生成一个独立的 SessionID 用于后续隔离
        if ("AGENT".equals(memberEntity.getMemberType()) && (memberEntity.getAgentSessionId() == null)) {
            memberEntity.setAgentSessionId("session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        
        log.info("【房间管理】成员加入房间：roomId={}, memberId={}, type={}", 
                memberEntity.getRoomId(), memberEntity.getMemberId(), memberEntity.getMemberType());
        chatRoomRepository.saveMember(memberEntity);
        return true;
    }

    @Override
    public boolean leaveRoom(String roomId, String memberId) {
        log.info("【房间管理】成员离开房间：roomId={}, memberId={}", roomId, memberId);
        chatRoomRepository.deleteMember(roomId, memberId);
        return true;
    }

    @Override
    public List<AiChatRoomMemberEntity> queryAgentsInRoom(String roomId) {
        return chatRoomRepository.queryAgentsByRoomId(roomId);
    }

    @Override
    public List<AiChatRoomMemberEntity> queryMembersInRoom(String roomId) {
        return chatRoomRepository.queryMembersByRoomId(roomId);
    }
}
