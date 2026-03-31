package com.dasi.domain.room.service.room;

import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.room
 * @Author: xerina
 * @CreateTime: 2026-03-25  17:47
 * @Description: 房间成员处理策略接口
 */
public interface IRoomMemberInsetService {

    String queryMemberName(String memberId , String memberType);

    /**
     * 加入房间处理逻辑
     * @param memberEntity 成员实体
     * @return 成功与否
     */
    boolean joinRoom(AiChatRoomMemberEntity memberEntity);
}
