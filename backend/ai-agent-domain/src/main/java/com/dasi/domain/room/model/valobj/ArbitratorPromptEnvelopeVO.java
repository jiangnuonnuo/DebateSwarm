package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: xerina
 * @Description: 仲裁者提示词封装（运行时，不入库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArbitratorPromptEnvelopeVO {

    /**
     * 运行时 system 提示词
     */
    private String systemPrompt;

    /**
     * 运行时 user 提示词
     */
    private String userPrompt;

    /**
     * 动态指令注入模式：PLACEHOLDER_REPLACED / APPENDED / FALLBACK_ONLY
     */
    private String mode;

    /**
     * 动态指令摘要（长度 + hash），用于日志排障
     */
    private String instructionDigest;
}
