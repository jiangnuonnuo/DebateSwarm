package com.dasi.domain.room.service.room;

import com.dasi.domain.ai.model.vo.AiClientVO;
import com.dasi.domain.ai.repository.IAiRepository;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.service.IRoomAdminService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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


    @Resource
    private Map<String, IRoomMemberInsetService> roomMemberInsetServiceMap;

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
    public List<AiChatRoomEntity> queryRoomsByMemberId(String memberId) {
        return chatRoomRepository.queryRoomsByMemberId(memberId);
    }

    @Override
    public boolean joinRoom(AiChatRoomMemberEntity memberEntity) {
        if (memberEntity.getRoomId() == null || memberEntity.getMemberId() == null) {
            log.warn("【房间管理】加入房间失败：roomId 或 memberId 不能为空");
            return false;
        }

        String strategyKey = memberEntity.getMemberType() + "_MEMBER";
        IRoomMemberInsetService strategy = roomMemberInsetServiceMap.get(strategyKey);

        if (strategy == null) {
            log.error("【房间管理】未找到对应的成员处理策略：type={}", memberEntity.getMemberType());
            return false;
        }

        // 调用具体策略执行加入逻辑 (内部包含 basicJoin 和 doJoin)
        return strategy.joinRoom(memberEntity);
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

    @Override
    public List<AiChatRoomMessageEntity> queryMessagesByCursor(String roomId, Long cursorTime, Integer limit) {
        log.info("【房间管理】游标查询消息：roomId={}, cursorTime={}, limit={}", roomId, cursorTime, limit);
        return chatRoomRepository.queryMessagesByCursor(roomId, cursorTime, limit);
    }

}
