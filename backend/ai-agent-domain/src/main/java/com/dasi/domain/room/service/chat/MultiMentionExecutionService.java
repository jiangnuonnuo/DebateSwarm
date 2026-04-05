package com.dasi.domain.room.service.chat;

import com.dasi.domain.room.model.valobj.ClientExecutionRequestVO;
import com.dasi.domain.room.model.valobj.ClientReplyResultVO;
import com.dasi.domain.room.service.IRoomChatService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Semaphore;

/**
 * @Author: xerina
 * @Description: Multi-At 批量执行服务实现
 * 职责：并发生成多个 client 回复，并在完成时立即发布；并发数由统一闸门控制，避免房间内瞬时外呼失控。
 */
@Slf4j
@Service
public class MultiMentionExecutionService implements IMultiMentionExecutionService {

    @Resource
    private IRoomChatService roomChatService;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Value("${room.chat.multi-at.max-concurrency:3}")
    private int multiAtMaxConcurrency;

    private Semaphore multiAtSemaphore;

    @PostConstruct
    public void init() {
        multiAtSemaphore = new Semaphore(Math.max(1, multiAtMaxConcurrency), true);
    }

    @Override
    public void executeBatch(List<ClientExecutionRequestVO> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        String roomId = requests.get(0).getRoomId();
        String batchTraceId = requests.get(0).getBatchTraceId();
        log.info("【MULTI_AT_EXECUTE】提交批量任务 roomId={}, batchTraceId={}, size={}",
                roomId, batchTraceId, requests.size());

        requests.forEach(request -> CompletableFuture
                .supplyAsync(() -> guardedGenerate(request), threadPoolExecutor)
                .whenComplete((result, throwable) -> publishResult(request, result, throwable)));
    }

    private ClientReplyResultVO guardedGenerate(ClientExecutionRequestVO request) {
        multiAtSemaphore.acquireUninterruptibly();
        try {
            return roomChatService.generateClientReply(request);
        } finally {
            multiAtSemaphore.release();
        }
    }

    private void publishResult(ClientExecutionRequestVO request, ClientReplyResultVO result, Throwable throwable) {
        ClientReplyResultVO finalResult = result;
        if (finalResult == null) {
            finalResult = ClientReplyResultVO.builder()
                    .roomId(request.getRoomId())
                    .clientId(request.getClientId())
                    .clientName(request.getClientId())
                    .success(false)
                    .errorType("EXECUTION_FAILED")
                    .errorMessage(throwable == null ? "批量执行异常" : unwrapMessage(throwable))
                    .batchTraceId(request.getBatchTraceId())
                    .orderIndex(request.getOrderIndex())
                    .requestedAtMemberIds(request.getRequestedAtMemberIds())
                    .startedAt(System.currentTimeMillis())
                    .finishedAt(System.currentTimeMillis())
                    .build();
        }

        log.info("【MULTI_AT_RESULT_READY】roomId={}, clientId={}, batchTraceId={}, orderIndex={}, success={}, errorType={}",
                finalResult.getRoomId(),
                finalResult.getClientId(),
                finalResult.getBatchTraceId(),
                finalResult.getOrderIndex(),
                finalResult.isSuccess(),
                finalResult.getErrorType());

        try {
            roomChatService.publishClientReplyResult(finalResult);
        } catch (Exception e) {
            log.error("【MULTI_AT_ITEM_FAILED】结果发布失败 roomId={}, clientId={}, batchTraceId={}",
                    finalResult.getRoomId(), finalResult.getClientId(), finalResult.getBatchTraceId(), e);
        }
    }

    private String unwrapMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
