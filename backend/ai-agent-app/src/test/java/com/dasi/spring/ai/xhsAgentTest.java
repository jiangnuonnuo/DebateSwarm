package com.dasi.spring.ai;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @BelongsProject: MeetingController.java
 * @BelongsPackage: com.dasi.spring.ai
 * @Author: xerina
 * @CreateTime: 2026-04-30  17:41
 * @Description: TODO
 */

@Slf4j
@SpringBootTest
public class xhsAgentTest {


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



}
