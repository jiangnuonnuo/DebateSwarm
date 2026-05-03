package com.dasi.domain.xhspublish.service.bmode.intelligent.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import com.dasi.domain.user.model.vo.QueryChatClientVO;
import com.dasi.domain.user.service.query.IQueryService;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service("bPublishClientCheckNode")
public class BPublishClientCheckNode extends AbstractBPublishIntelligentNode {

    @Resource
    private IQueryService queryService;

    @Resource
    private BPublishInputNormalizeNode inputNormalizeNode;

    @Override
    protected String doApply(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) throws Exception {
        // 智能预处理第一步：先校验当前用户可用的生成 client，避免草稿创建后才发现 client 配置无效。
        String clientId = dynamicContext.getCommand().getClientId();
        if (!StringUtils.hasText(clientId)) {
            throw new WorkException("发布生成 clientId 不能为空");
        }
        List<QueryChatClientVO> clientList = queryService.queryChatClientList();
        boolean exists = clientList != null && clientList.stream().anyMatch(item -> clientId.equals(item.getClientId()));
        if (!exists) {
            throw new WorkException("发布生成 client 不存在或未启用");
        }
        log.info("【小红书发布】B智能预处理-client校验通过：clientId={}", dynamicContext.getCommand().getClientId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<BPublishIntelligentContext, BPublishIntelligentContext, String> get(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) {
        return inputNormalizeNode;
    }

}
