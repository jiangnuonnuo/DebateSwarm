package com.dasi.domain.xhspublish.service.domain.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.valobj.PublishTypeVO;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.rule.XhsPublishRuleSupport;
import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PublishValidationDomainServiceImpl implements IPublishValidationDomainService {

    @Override
    public void validateTaskRequest(String publishType, String requestJson) {
        validatePublishType(publishType);
        if (!StringUtils.hasText(requestJson)) {
            throw new WorkException("发布上下文不能为空");
        }
        try {
            JSON.parseObject(requestJson);
        } catch (Exception e) {
            throw new WorkException("发布上下文必须是合法 JSON 对象");
        }
    }

    @Override
    public void validatePublishPayload(String publishType, String publishRequestJson) {
        validatePublishType(publishType);
        JSONObject payload = JSON.parseObject(publishRequestJson);
        String title = payload.getString("title");
        String content = payload.getString("content");
        List<String> images = payload.getList("images", String.class);
        if (!StringUtils.hasText(title)) {
            throw new WorkException("标题不能为空");
        }
        if (title.length() > 20) {
            throw new WorkException("标题长度不能超过 20 个字符");
        }
        if (!StringUtils.hasText(content)) {
            throw new WorkException("正文不能为空");
        }
        if (PublishTypeVO.image.name().equalsIgnoreCase(publishType) && (images == null || images.isEmpty())) {
            throw new WorkException("图文发布至少需要 1 张图片");
        }
        XhsPublishRuleSupport.normalizeVisibility(payload.getString("visibility"));
        XhsPublishRuleSupport.normalizeSchedule(payload.getString("schedule_at"));
    }

    private void validatePublishType(String publishType) {
        if (!PublishTypeVO.image.name().equalsIgnoreCase(publishType) && !PublishTypeVO.video.name().equalsIgnoreCase(publishType)) {
            throw new WorkException("当前仅支持 image/video 发布类型");
        }
    }

}
