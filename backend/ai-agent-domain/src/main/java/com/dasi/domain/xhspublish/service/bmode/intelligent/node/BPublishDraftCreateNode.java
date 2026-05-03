package com.dasi.domain.xhspublish.service.bmode.intelligent.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.model.entity.XhsPublishCreateTaskCommandEntity;
import com.dasi.domain.xhspublish.model.valobj.PublishModeVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTypeVO;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import com.dasi.domain.xhspublish.service.task.XhsPublishTaskCommandDomainService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("bPublishDraftCreateNode")
public class BPublishDraftCreateNode extends AbstractBPublishIntelligentNode {

    @Resource
    private XhsPublishTaskCommandDomainService taskCommandDomainService;

    @Resource
    private BPublishMaterialRegisterNode materialRegisterNode;

    @Override
    protected String doApply(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) throws Exception {
        // 第 3 步：创建 B 模式图文发布草稿，让后续素材登记、文案生成都围绕 taskId 继续推进。
        String taskName = dynamicContext.getCommand().getTaskName().trim();
        String publishMode = PublishModeVO.B.name();
        String publishType = PublishTypeVO.image.name();
        String bindingId = dynamicContext.getCommand().getBindingId();
        java.time.LocalDateTime scheduledPublishAt = dynamicContext.getCommand().getScheduledPublishAt();
        String requestJson = "{}";

        XhsPublishCreateTaskCommandEntity command = XhsPublishCreateTaskCommandEntity.builder()
                .taskName(taskName)
                .publishMode(publishMode)
                .publishType(publishType)
                .bindingId(bindingId)
                .scheduledPublishAt(scheduledPublishAt)
                .requestJson(requestJson)
                .build();

        String taskId = taskCommandDomainService.createTask(command);
        dynamicContext.setTaskId(taskId);
        log.info("【小红书发布】B智能预处理-草稿创建完成：taskId={}", taskId);
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<BPublishIntelligentContext, BPublishIntelligentContext, String> get(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) {
        return materialRegisterNode;
    }

}
