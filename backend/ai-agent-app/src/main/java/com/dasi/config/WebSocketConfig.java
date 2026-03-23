package com.dasi.config;

import com.dasi.trigger.websocket.ChatRoomSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.Map;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.config
 * @Author: xerina
 * @CreateTime: 2026-03-23  18:19
 * @Description: WebSocket 核心配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private ChatRoomSocketHandler chatRoomSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册群聊 Handler 路径：/ws/room/{roomId}/{userId}
        // 使用 Interceptor 提前解析路径参数
        registry.addHandler(chatRoomSocketHandler, "/ws/room/*/*")
                .addInterceptors(roomHandshakeInterceptor())
                .setAllowedOrigins("*");
    }

    @Bean
    public HandshakeInterceptor roomHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                         WebSocketHandler wsHandler, Map<String, Object> attributes) {
                // 路径格式: /ws/room/{roomId}/{userId}
                String path = request.getURI().getPath();
                String[] parts = path.split("/");
                if (parts.length >= 5) {
                    attributes.put("roomId", parts[3]);
                    attributes.put("userId", parts[4]);
                    return true;
                }
                return false;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                     WebSocketHandler wsHandler, Exception exception) {
            }
        };
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
