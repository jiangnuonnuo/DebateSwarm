package com.dasi.domain.room.service.room.impl;

import com.dasi.domain.ai.model.enumeration.AiArmoryType;
import com.dasi.domain.ai.model.vo.AiClientVO;
import com.dasi.domain.ai.model.vo.AiPromptVO;
import com.dasi.domain.ai.service.dispatch.IDispatchService;
import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.util.redis.IRedisUtil;
import com.dasi.types.constant.RedisConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.room.impl
 * @Author: xerina
 * @CreateTime: 2026-03-25  17:58
 * @Description: 客户端成员处理策略
 */
@Slf4j
@Service("CLIENT_MEMBER")
public class ClientMemberService extends AbstractRoomMemberService {

    @Resource
    private IRedisUtil redisUtil;

    @Resource
    private IDispatchService dispatchService;

    @Value("classpath:prompt/system-prompt/group-chat-template.txt")
    private org.springframework.core.io.Resource groupChatTemplate;

    @Override
    public String queryMemberName(String memberId, String memberType) {
        AiClientVO clientVO = aiRepository.queryAiClientVO(memberId);
        return clientVO != null ? clientVO.getClientName() : "未知客户端";
    }

    @Override
    protected boolean doJoin(AiChatRoomMemberEntity memberEntity) {
        // 客户端入场 -> 重构系统提示词 -> 覆盖装配
        rebuildSystemPrompt(memberEntity.getRoomId(), memberEntity.getMemberId());
        return true;
    }

    /**
     * 重构并覆盖系统提示词 (入场 -> 重构 -> 覆盖)
     * @param roomId   房间ID
     * @param clientId 智能体/客户端ID
     */
    private void rebuildSystemPrompt(String roomId, String clientId) {
        log.info("【提示词重构】开始为 Bot 重构系统提示词：roomId={}, clientId={}", roomId, clientId);

        try {
            // 1. 采集数据 (原料)
            AiChatRoomEntity room = chatRoomRepository.queryRoomById(roomId);
            String roomName = room != null ? room.getRoomName() : "未知房间";
            String roomDesc = (room != null && room.getRoomDesc() != null) ? room.getRoomDesc() : "暂无背景描述";

            // 2. 获取原始人设 (使用新增的轻量级查询方法，直接按 clientId 获取)
            AiPromptVO originalPromptVO = aiRepository.queryPromptByClientId(clientId);
            if (originalPromptVO == null) {
                log.warn("【提示词重构】未找到 Bot 原始提示词配置：clientId={}", clientId);
                return;
            }
            String originalPrompt = originalPromptVO.getSystemPrompt();

            // 3. 读取并合成模板 (重构)
            // 增加防御逻辑：如果 originalPrompt 已经是合成过的，尝试提取其中的原始部分，防止多次嵌套
            if (originalPrompt.contains("# 你的原始人设 (Original Identity)")) {
                int start = originalPrompt.indexOf("# 你的原始人设 (Original Identity)") + "# 你的原始人设 (Original Identity)".length();
                int end = originalPrompt.indexOf("# 当前群聊场景 (Group Chat Context)");
                if (end > start) {
                    originalPrompt = originalPrompt.substring(start, end).trim();
                }
            }

            if (groupChatTemplate == null || !groupChatTemplate.exists()) {
                log.error("【提示词重构】未找到群聊提示词模板文件：classpath:prompt/system-prompt/group-chat-template.txt");
                return;
            }

            String templateContent;
            try (InputStream inputStream = groupChatTemplate.getInputStream()) {
                templateContent = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }

            if (templateContent.isEmpty()) {
                log.warn("【提示词重构】提示词模板内容为空");
                return;
            }

            String finalPrompt = templateContent
                    .replace("{originalPrompt}", originalPrompt)
                    .replace("{roomName}", roomName)
                    .replace("{roomDesc}", roomDesc);

            // 4. 持久化与强制装配 (覆盖)
            // 4.1) 更新 DB (通过 Repository 调用，遵循 DDD)
            aiRepository.updateSystenByPromptId(originalPromptVO.getPromptId(), finalPrompt);

            // 4.2) 清除 Redis 缓存 (原料刷新)
            redisUtil.deleteByKey(RedisConstant.AI_PROMPT_VO_PREFIX + originalPromptVO.getPromptId());
            redisUtil.deleteByKey(RedisConstant.AI_CLIENT_VO_PREFIX + clientId);

            // 4.3) 强制重新装配 (工厂生产，内存覆盖)
            dispatchService.dispatchArmoryStrategy(AiArmoryType.ARMORY_CHAT.getType(), Collections.singleton(clientId));

            log.info("【提示词重构】Bot 系统提示词重构并覆盖装配完成：roomId={}, clientId={}", roomId, clientId);

        } catch (Exception e) {
            log.error("【提示词重构】Bot 系统提示词重构失败：roomId={}, clientId={}", roomId, clientId, e);
        }
    }
}
