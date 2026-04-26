package com.dasi.config;

import com.dasi.domain.xhspublish.config.XhsPublishProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@EnableConfigurationProperties(XhsPublishProperties.class)
public class XhsMcpClientConfig {

    @Bean("xhsPublishMcpClient")
    public McpSyncClient xhsPublishMcpClient(XhsPublishProperties properties) {
        McpClientTransport transport = createTransport(properties);

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofMinutes(properties.getMcpTimeoutMinutes()))
                .build();

        client.initialize();
        log.info("【小红书发布】MCP 客户端初始化完成：baseUrl={}, endpoint={}", properties.getMcpBaseUrl(), properties.getMcpEndpoint());
        return client;
    }

    private McpClientTransport createTransport(XhsPublishProperties properties) {
        try {
            Class<?> transportClass = Class.forName("io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport");
            Object builder = transportClass.getMethod("builder", String.class).invoke(null, properties.getMcpBaseUrl());
            builder.getClass().getMethod("endpoint", String.class).invoke(builder, properties.getMcpEndpoint());
            Object transport = builder.getClass().getMethod("build").invoke(builder);
            log.info("【小红书发布】使用 Streamable HTTP MCP 传输层");
            return (McpClientTransport) transport;
        } catch (ClassNotFoundException e) {
            log.warn("【小红书发布】当前依赖未提供 Streamable HTTP MCP 客户端，降级为 SSE 传输。若要直连 /mcp，请补充 streamable client 依赖。");
            return HttpClientSseClientTransport.builder(properties.getMcpBaseUrl())
                    .sseEndpoint(properties.getMcpEndpoint())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("初始化小红书 MCP 传输层失败", e);
        }
    }

}
