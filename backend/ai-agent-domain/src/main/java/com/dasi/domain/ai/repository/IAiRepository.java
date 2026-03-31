package com.dasi.domain.ai.repository;

import com.dasi.domain.ai.model.vo.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IAiRepository {

    Set<AiClientVO> queryAiClientVOSetByClientIdSet(Set<String> clientIdSet);

    AiClientVO queryAiClientVO(String clientId);

    Set<AiAdvisorVO> queryAiAdvisorVOSetByClientIdSet(Set<String> clientIdSet);

    Map<String, AiPromptVO> queryAiPromptVOMapByClientIdSet(Set<String> clientIdSet);

    Set<AiMcpVO> queryAiMcpVOSetByClientIdSet(Set<String> clientIdSet);

    Set<AiModelVO> queryAiModelVOSetByClientIdSet(Set<String> clientIdSet);

    Set<AiApiVO> queryAiApiVOSetByClientIdSet(Set<String> clientIdSet);

    Map<String, AiFlowVO> queryAiFlowVOMapByAgentId(String agentId);

    String queryExecuteTypeByAgentId(String agentId);

    List<AiTaskVO> queryTaskVOList();

    String queryAgentNameByAgentId(String agentId);

    List<AiMcpVO> queryAiMcpVOListByMcpIdList(List<String> mcpIdList);

    /**
     * 更新系统提示词内容
     * @param promptId      提示词ID
     * @param systemPrompt  系统提示词内容
     */
    void updateSystenByPromptId(String promptId, String systemPrompt);

    /**
     * 根据 clientId 查询关联的提示词信息 (轻量级)
     * @param clientId 客户端ID
     * @return 提示词VO
     */
    AiPromptVO queryPromptByClientId(String clientId);
}
