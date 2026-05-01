package com.dasi.domain.xhspublish.service.knowledge;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.adapter.port.IXhsVectorStorePort;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishKnowledgeDocRepository;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishKnowledgeDocEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Service
public class XhsPublishKnowledgeIngestionService {

    private static final int MAX_SUMMARY_LENGTH = 4000;

    @Resource
    private IXhsPublishKnowledgeDocRepository knowledgeDocRepository;

    @Resource
    private IXhsVectorStorePort vectorStorePort;

    @Resource
    private XhsPublishIdSupport idSupport;

    /**
     * 发布成功后的知识沉淀入口：
     * 1) 从上下文提取标题/正文/标签，并补充帖子链接；
     * 2) 写知识主档并向量化；
     * 3) 任何异常仅记录日志，不反向影响发布成功主流程。
     */
    public void ingestPublishSuccess(XhsPublishTaskEntity task,
                                     XhsPublishAttemptEntity attempt,
                                     XhsPublishRemoteResult remoteResult) {
        try {
            String summary = buildSummary(task, remoteResult);
            if (!StringUtils.hasText(summary) || task == null || task.getUserId() == null) {
                return;
            }

            String knowledgeId = idSupport.nextKnowledgeId();
            XhsPublishKnowledgeDocEntity entity = XhsPublishKnowledgeDocEntity.builder()
                    .knowledgeId(knowledgeId)
                    .userId(task.getUserId())
                    .knowledgeScope("personal")
                    .knowledgeType("publish_case")
                    .title(resolveTitle(task))
                    .ragTag("xhs_publish")
                    .sourceType("publish_success")
                    .sourceRef(buildSourceRef(task, attempt, remoteResult))
                    .summary(summary)
                    .docStatus("active")
                    .vectorStatus("pending")
                    .build();
            knowledgeDocRepository.insert(entity);

            XhsPublishKnowledgeDocEntity persisted = knowledgeDocRepository.queryByKnowledgeId(knowledgeId);
            if (persisted == null || persisted.getId() == null) {
                return;
            }

            try {
                vectorStorePort.upsert(summary, buildMetadata(task, attempt, knowledgeId));
                persisted.setVectorStatus("success");
            } catch (Exception vectorException) {
                persisted.setVectorStatus("failed");
                log.warn("【小红书发布】知识沉淀向量写入失败：taskId={}, attemptId={}, knowledgeId={}, message={}",
                        task.getTaskId(),
                        attempt == null ? null : attempt.getAttemptId(),
                        knowledgeId,
                        trimMessage(vectorException.getMessage()));
            }
            knowledgeDocRepository.update(persisted);
        } catch (Exception e) {
            log.warn("【小红书发布】发布成功后知识沉淀失败（已忽略，不影响主流程）：taskId={}, attemptId={}, message={}",
                    task == null ? null : task.getTaskId(),
                    attempt == null ? null : attempt.getAttemptId(),
                    trimMessage(e.getMessage()));
        }
    }

    private String buildSummary(XhsPublishTaskEntity task, XhsPublishRemoteResult remoteResult) {
        if (task == null) {
            return null;
        }
        JSONObject context = parseContext(task);
        if (context == null) {
            return null;
        }

        String title = firstNonBlank(context.getString("title"), task.getTaskName());
        String content = context.getString("content");
        String tags = joinTags(context.getJSONArray("tags"));
        String postUrl = extractPostUrl(remoteResult);

        StringBuilder builder = new StringBuilder();
        appendLine(builder, "标题", title);
        appendLine(builder, "正文", content);
        appendLine(builder, "标签", tags);
        appendLine(builder, "发布链接", postUrl);

        String result = builder.toString().trim();
        if (!StringUtils.hasText(result)) {
            return null;
        }
        if (result.length() > MAX_SUMMARY_LENGTH) {
            return result.substring(0, MAX_SUMMARY_LENGTH);
        }
        return result;
    }

    private JSONObject parseContext(XhsPublishTaskEntity task) {
        String contextJson = StringUtils.hasText(task.getLatestContextJson()) ? task.getLatestContextJson() : task.getRequestJson();
        if (!StringUtils.hasText(contextJson)) {
            return null;
        }
        try {
            return JSON.parseObject(contextJson);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String resolveTitle(XhsPublishTaskEntity task) {
        JSONObject context = parseContext(task);
        return firstNonBlank(context == null ? null : context.getString("title"), task == null ? null : task.getTaskName());
    }

    private String buildSourceRef(XhsPublishTaskEntity task,
                                  XhsPublishAttemptEntity attempt,
                                  XhsPublishRemoteResult remoteResult) {
        StringJoiner joiner = new StringJoiner(";");
        if (task != null && StringUtils.hasText(task.getTaskId())) {
            joiner.add("taskId=" + task.getTaskId());
        }
        if (attempt != null && StringUtils.hasText(attempt.getAttemptId())) {
            joiner.add("attemptId=" + attempt.getAttemptId());
        }
        String postUrl = extractPostUrl(remoteResult);
        if (StringUtils.hasText(postUrl)) {
            joiner.add("postUrl=" + postUrl);
        }
        return joiner.toString();
    }

    private String extractPostUrl(XhsPublishRemoteResult remoteResult) {
        if (remoteResult == null || !StringUtils.hasText(remoteResult.getResultJson())) {
            return null;
        }
        try {
            JSONObject result = JSON.parseObject(remoteResult.getResultJson());
            String direct = firstNonBlank(result.getString("postUrl"), result.getString("post_url"));
            if (StringUtils.hasText(direct)) {
                return direct;
            }
            JSONObject data = result.getJSONObject("data");
            if (data == null) {
                return null;
            }
            return firstNonBlank(data.getString("postUrl"), data.getString("post_url"));
        } catch (Exception ignore) {
            return null;
        }
    }

    private Map<String, Object> buildMetadata(XhsPublishTaskEntity task,
                                              XhsPublishAttemptEntity attempt,
                                              String knowledgeId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("bizDomain", "xhs_publish");
        metadata.put("knowledgeId", knowledgeId);
        metadata.put("knowledge", "xhs_publish");
        metadata.put("knowledgeScope", "personal");
        metadata.put("knowledgeType", "publish_case");
        metadata.put("status", "active");
        metadata.put("sourceType", "publish_success");
        metadata.put("userId", String.valueOf(task.getUserId()));
        metadata.put("taskId", task.getTaskId());
        metadata.put("attemptId", attempt == null ? null : attempt.getAttemptId());
        return metadata;
    }

    private String joinTags(JSONArray tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Object tag : tags) {
            if (tag != null && StringUtils.hasText(String.valueOf(tag))) {
                joiner.add(String.valueOf(tag));
            }
        }
        String text = joiner.toString();
        return StringUtils.hasText(text) ? text : null;
    }

    private void appendLine(StringBuilder builder, String key, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(key).append('：').append(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown";
        }
        String normalized = message.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 220) {
            return normalized;
        }
        return normalized.substring(0, 220) + "...";
    }

}
