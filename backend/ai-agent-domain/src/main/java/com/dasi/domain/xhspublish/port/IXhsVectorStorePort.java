package com.dasi.domain.xhspublish.port;

import java.util.List;
import java.util.Map;

public interface IXhsVectorStorePort {

    void upsert(String content, Map<String, Object> metadata);

    void deleteByKnowledgeId(String knowledgeId);

    List<String> recallPersonalThenShared(String query, Long userId, String knowledge);

}
