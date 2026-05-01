package com.dasi.config;

import com.dasi.domain.xhspublish.config.XhsPublishProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
@Slf4j
@SpringBootTest
class XhsMcpClientConfigTest {

    String textPrompt = "你是一个小红书发布人员，你需要整理一篇关于AI使用的文档教学，纯文本形式的，title 5个字的";
    String publishPrompt = "角色：你负责根据content字段的内容发布小红书的帖子，如果任务执行成功，只返回success，失败则返回错误原因";


    @Test
    public void testXhsAgent(){

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofMinutes(5));


        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey("sk-01a9173d124e49e2bd1c5cd48affaf18")
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .completionsPath("/v1/chat/completions")
                .embeddingsPath("/v1/embeddings")
                .restClientBuilder(RestClient.builder().requestFactory(factory))
                .build();

        ChatModel textModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("qwen3.6-plus")
                                .build()
                )
                .build();

        ChatClient textClient = ChatClient.builder(textModel)
                .build();


        ChatModel publishModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("qwen3.6-plus")
                                .toolCallbacks(new SyncMcpToolCallbackProvider(sseMcpClient01()).getToolCallbacks())
                                .build()
                )
                .build();


        String content = textClient.prompt(textPrompt).call().content();
        log.info("content:{}",content);
        String context = "";

        for(int i = 0 ; i < 3 ;i++){
            try {
                String message = publishPrompt +"content:"+content + "上下文:"+context+"上传图片的位置为“F:\\school\\images\\1.jpg";
                String call = publishModel.call(message);

                log.info("ai 回答结果为"+ call);
                if("success".equals(call)){
                    return ;
                }
                StringBuilder sb = new StringBuilder(content);
                sb.append(call);

                content = sb.toString();
            }catch (Exception e){

            }
        }


    }

    public McpSyncClient sseMcpClient01() {

        HttpClientStreamableHttpTransport httpClientTransport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:18060")
                .endpoint("/mcp")
                .build();

        McpSyncClient mcpSyncClient = McpClient.sync(httpClientTransport).requestTimeout(Duration.ofMinutes(36000)).build();
        var init_sse = mcpSyncClient.initialize();
        log.info("Tool SSE MCP02 Initialized {}", init_sse);

        return mcpSyncClient;
    }


}
