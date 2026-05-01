package com.dasi.domain.xhspublish.service.generator;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.service.rule.XhsPublishRuleSupport;
import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class XhsPublishGeneratedContentParser {

    public GeneratedPublishContext parse(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new WorkException("智能生成结果为空");
        }
        JSONObject jsonObject;
        try {
            jsonObject = JSON.parseObject(stripCodeFence(rawResponse));
        } catch (Exception e) {
            throw new WorkException("智能生成结果不是合法 JSON");
        }
        String title = XhsPublishRuleSupport.readString(jsonObject, "title");
        if (!StringUtils.hasText(title)) {
            throw new WorkException("智能生成结果缺少标题");
        }
        title = title.trim();
        if (title.length() > 20) {
            throw new WorkException("智能生成标题长度不能超过 20 个字符");
        }

        String content = XhsPublishRuleSupport.readString(jsonObject, "content");
        if (!StringUtils.hasText(content)) {
            throw new WorkException("智能生成结果缺少正文");
        }

        Boolean isOriginal = null;
        if (jsonObject.containsKey("is_original")) {
            isOriginal = XhsPublishRuleSupport.readBoolean(jsonObject, "is_original", false);
        } else if (jsonObject.containsKey("isOriginal")) {
            isOriginal = XhsPublishRuleSupport.readBoolean(jsonObject, "isOriginal", false);
        }

        return GeneratedPublishContext.builder()
                .title(title)
                .content(content.trim())
                .tags(XhsPublishRuleSupport.readStringList(jsonObject, "tags"))
                .visibility(XhsPublishRuleSupport.readString(jsonObject, "visibility"))
                .isOriginal(isOriginal)
                .build();
    }

    private String stripCodeFence(String rawResponse) {
        String text = rawResponse.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineBreak = text.indexOf('\n');
        if (firstLineBreak < 0) {
            return text;
        }
        String body = text.substring(firstLineBreak + 1);
        int lastFence = body.lastIndexOf("```");
        if (lastFence >= 0) {
            body = body.substring(0, lastFence);
        }
        return body.trim();
    }

}
