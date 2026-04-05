package com.dasi.domain.room.service.chat;

import com.dasi.domain.room.model.valobj.ClientExecutionRequestVO;

import java.util.List;

/**
 * @Author: xerina
 * @Description: Multi-At 批量执行服务
 * 职责：在自由聊天模式下并发生成多个 client 回复，并按完成时间即时对外发布。
 */
public interface IMultiMentionExecutionService {

    /**
     * 提交一批自由聊天 Multi-At 执行任务
     */
    void executeBatch(List<ClientExecutionRequestVO> requests);
}
