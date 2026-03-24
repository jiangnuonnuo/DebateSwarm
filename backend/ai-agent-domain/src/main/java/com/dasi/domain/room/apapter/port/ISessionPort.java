package com.dasi.domain.room.apapter.port;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.apapter.port
 * @Author: xerina
 * @CreateTime: 2026-03-23  23:10
 * @Description: 会话管理端口接口 (领域层契约)
 */
public interface ISessionPort {

    /**
     * 保存会话
     */
    void addSession(String roomId, String userId, Object session);

    /**
     * 移除会话
     */
    void removeSession(String roomId, String userId);

}
