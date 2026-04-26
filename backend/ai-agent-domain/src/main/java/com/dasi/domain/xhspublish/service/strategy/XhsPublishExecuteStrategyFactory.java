package com.dasi.domain.xhspublish.service.strategy;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class XhsPublishExecuteStrategyFactory {

    private final Map<String, IXhsPublishExecuteStrategy> strategyMap = new ConcurrentHashMap<>();

    public XhsPublishExecuteStrategyFactory(Map<String, IXhsPublishExecuteStrategy> executeStrategyMap) {
        executeStrategyMap.values().forEach(strategy -> strategyMap.put(strategy.getKey(), strategy));
    }

    public IXhsPublishExecuteStrategy getStrategy(String publishMode, String publishType) {
        return strategyMap.get(buildKey(publishMode, publishType));
    }

    public static String buildKey(String publishMode, String publishType) {
        return publishMode + ":" + publishType;
    }

}
