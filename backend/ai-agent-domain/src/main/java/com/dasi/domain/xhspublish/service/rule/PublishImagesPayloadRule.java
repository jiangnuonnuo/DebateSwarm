package com.dasi.domain.xhspublish.service.rule;

import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublishImagesPayloadRule implements IXhsPublishPayloadRule {

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    public void apply(XhsPublishRuleContext context) {
        List<String> imageList = XhsPublishRuleSupport.resolveImages(context.getSourceContext(), context.getAssetList());
        if (imageList == null || imageList.isEmpty()) {
            throw new WorkException("图文发布至少需要 1 张图片，请先上传图片或在上下文中提供 images");
        }
        context.getPublishPayload().put("images", imageList);
    }

}
