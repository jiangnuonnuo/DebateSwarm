package com.dasi.infrastructure.adapter.port.xhspublish;

import com.dasi.domain.xhspublish.config.XhsPublishProperties;
import com.dasi.domain.xhspublish.adapter.port.IXhsVectorStorePort;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class XhsVectorStoreAdapter implements IXhsVectorStorePort {

    @Resource
    @Qualifier("xhsPgVectorStore")
    private PgVectorStore xhsPgVectorStore;

    @Resource
    @Qualifier("postgresqlTemplate")
    private JdbcTemplate postgresqlTemplate;

    @Resource
    private XhsPublishProperties xhsPublishProperties;

    @Override
    public void upsert(String content, Map<String, Object> metadata) {
        Document document = new Document(content, metadata);
        xhsPgVectorStore.add(List.of(document));
    }

    @Override
    public void deleteByKnowledgeId(String knowledgeId) {
        String tableName = xhsPublishProperties.getVectorSchemaName() + "." + xhsPublishProperties.getVectorTableName();
        postgresqlTemplate.update("DELETE FROM " + tableName + " WHERE metadata->>'knowledgeId' = ?", knowledgeId);
    }

    @Override
    public List<String> recallPersonalThenShared(String query, Long userId, String knowledge) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        Filter.Expression personal = filterBuilder.and(
                filterBuilder.and(
                        filterBuilder.and(
                                filterBuilder.eq("bizDomain", "xhs_publish"),
                                filterBuilder.eq("knowledgeScope", "personal")
                        ),
                        filterBuilder.and(
                                filterBuilder.eq("status", "active"),
                                filterBuilder.eq("userId", String.valueOf(userId))
                        )
                ),
                filterBuilder.eq("knowledge", knowledge)
        ).build();
        Filter.Expression shared = filterBuilder.and(
                filterBuilder.and(
                        filterBuilder.eq("bizDomain", "xhs_publish"),
                        filterBuilder.eq("knowledgeScope", "shared")
                ),
                filterBuilder.and(
                        filterBuilder.eq("status", "active"),
                        filterBuilder.eq("knowledge", knowledge)
                )
        ).build();

        List<String> personalTexts = xhsPgVectorStore.similaritySearch(SearchRequest.builder().query(query).filterExpression(personal).topK(5).build())
                .stream().map(Document::getText).filter(Objects::nonNull).toList();
        if (personalTexts.size() >= 5) {
            return personalTexts;
        }

        List<String> sharedTexts = xhsPgVectorStore.similaritySearch(SearchRequest.builder().query(query).filterExpression(shared).topK(5 - personalTexts.size()).build())
                .stream().map(Document::getText).filter(Objects::nonNull).toList();
        return java.util.stream.Stream.concat(personalTexts.stream(), sharedTexts.stream()).toList();
    }

}

