package com.dasi.spring.ai;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.spring.ai
 * @Author: xerina
 * @CreateTime: 2026-03-20  20:42
 * @Description: TODO
 */

@Slf4j
@SpringBootTest
public class ApiTest {

    @Test
    public void test() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .completionsPath("/v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .apiKey("sk-68cf0ed784674f719ed06e3f30d9a523")
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("qwen-plus")
                                .build()
                )
                .build();

        ChatClient chatClient = ChatClient.builder(chatModel)
                .build();

        String result = chatClient.prompt("你是什么模型？").call().content();
        log.info(result);
    }


    public static McpSyncClient sseMcpClient01() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:18060")
                .endpoint("/mcp")
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofMinutes(36000))
                .build();

        return client;
    }

}
