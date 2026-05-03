package com.dasi.domain.xhspublish.service.intelligent;

import com.dasi.domain.xhspublish.model.entity.XhsPublishIntelligentSubmitCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishIntelligentSubmitResultEntity;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import com.dasi.domain.xhspublish.service.bmode.intelligent.support.BPublishIntelligentTreeFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
public class XhsPublishIntelligentSubmitDomainService {

    @Resource
    private BPublishIntelligentTreeFactory intelligentTreeFactory;

    public XhsPublishIntelligentSubmitResultEntity submit(XhsPublishIntelligentSubmitCommandEntity command, List<MultipartFile> fileList) {
        BPublishIntelligentContext context = BPublishIntelligentContext.builder()
                .command(command)
                .fileList(fileList)
                .build();
        try {
            intelligentTreeFactory.getRootNode().apply(context, context);
            return context.toResult();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("【小红书发布】B智能预处理树执行失败：taskName={}", command.getTaskName(), e);
            throw new RuntimeException(e);
        }
    }

}
