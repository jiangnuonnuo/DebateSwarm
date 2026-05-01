package com.dasi.config;

import com.dasi.properties.XhsPublishProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
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

    McpClientTransport createTransport(XhsPublishProperties properties) {
        log.info("【小红书发布】使用 Streamable HTTP MCP 传输层：baseUrl={}, endpoint={}",
                properties.getMcpBaseUrl(), properties.getMcpEndpoint());
        return HttpClientStreamableHttpTransport.builder(properties.getMcpBaseUrl())
                .endpoint(properties.getMcpEndpoint())
                .build();
    }

}
