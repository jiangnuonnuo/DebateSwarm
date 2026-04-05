package com.dasi.domain.room.service;

import com.dasi.domain.room.model.valobj.RoomChatRequest;
import com.dasi.domain.room.model.valobj.ClientExecutionRequestVO;
import com.dasi.domain.room.model.valobj.ClientReplyResultVO;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:06
 * @Description: 聊天室对话服务接口
 */
public interface IRoomChatService {

    /**
     * 处理用户上行消息
     */
    void onUserMessage(RoomChatRequest request);

    /**
     * 客户端发言 (由 DispatchService 调度)
     */
    void clientChat(ClientExecutionRequestVO request);

    /**
     * 仅生成 client 回复内容，不触发内部调度事件
     */
    ClientReplyResultVO generateClientReply(ClientExecutionRequestVO request);

    /**
     * 按生成结果对外发布消息；Multi-At 场景下不会触发 CLIENT_MSG_END
     */
    void publishClientReplyResult(ClientReplyResultVO result);

    /**
     * 广播系统通知并落库
     */
    void publishSystemNotice(String roomId, String noticeType, String content, String extData);

}
