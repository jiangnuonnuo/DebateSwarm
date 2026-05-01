package com.dasi.domain.xhspublish.service.intelligent.guard;

import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;

@Component
public class ImageSourceGuard implements IXhsPublishIntelligentSubmitGuard {

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public void validate(IntelligentSubmitGuardContext context) {
        boolean hasFile = context.getFileList() != null && !context.getFileList().isEmpty();
        boolean hasUrl = context.getOriginImageUrls() != null && !context.getOriginImageUrls().isEmpty();
        if (!hasFile && !hasUrl) {
            throw new WorkException("图文发布至少需要 1 张图片，请上传文件或提供远程图片地址");
        }
    }

}
