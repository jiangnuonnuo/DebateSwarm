package com.dasi.config;

import com.dasi.properties.XhsPublishProperties;
import com.dasi.properties.EmbeddingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Configuration
@EnableConfigurationProperties({EmbeddingProperties.class, XhsPublishProperties.class})
public class XhsPgVectorStoreConfig {

    @Bean("xhsPgVectorStore")
    public PgVectorStore xhsPgVectorStore(EmbeddingProperties embeddingProperties,
                                          XhsPublishProperties xhsPublishProperties,
                                          @Qualifier("postgresqlTemplate") JdbcTemplate jdbcTemplate) {

        log.info("【初始化配置】XhsPgVectorStore");

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(embeddingProperties.getBaseUrl())
                .apiKey(embeddingProperties.getApiKey())
                .build();

        OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model(embeddingProperties.getModel())
                .dimensions(embeddingProperties.getDimensions())
                .encodingFormat(embeddingProperties.getEncodingFormat())
                .build();

        OpenAiEmbeddingModel openAiEmbeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, embeddingOptions);

        return PgVectorStore.builder(jdbcTemplate, openAiEmbeddingModel)
                .initializeSchema(false)
                .schemaName(xhsPublishProperties.getVectorSchemaName())
                .vectorTableName(xhsPublishProperties.getVectorTableName())
                .build();
    }

}
