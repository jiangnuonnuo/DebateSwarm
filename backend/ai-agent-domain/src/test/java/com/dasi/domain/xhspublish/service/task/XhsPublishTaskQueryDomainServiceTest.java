package com.dasi.domain.xhspublish.service.task;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.dto.PageXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskDetailVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskPageVO;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishViewAssembler;
import com.dasi.types.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishTaskQueryDomainServiceTest {

    private XhsPublishTaskQueryDomainService service;

    @Mock
    private IXhsPublishRepository publishRepository;

    @Mock
    private XhsPublishTaskAccessSupport taskAccessSupport;

    @BeforeEach
    void setUp() {
        service = new XhsPublishTaskQueryDomainService();
        ReflectionTestUtils.setField(service, "publishRepository", publishRepository);
        ReflectionTestUtils.setField(service, "taskAccessSupport", taskAccessSupport);
        ReflectionTestUtils.setField(service, "viewAssembler", new XhsPublishViewAssembler());
    }

    @Test
    void shouldMapPageResultUsingLightweightFields() {
        when(taskAccessSupport.requireUserId()).thenReturn(1001L);
        when(publishRepository.pageTask(eq(1001L), eq("k"), eq("running"), eq("publishing"), eq(5), eq(5)))
                .thenReturn(List.of(XhsPublishTaskEntity.builder()
                        .taskId("task-1")
                        .taskName("t-1")
                        .publishMode("B")
                        .publishType("image")
                        .taskStatus("running")
                        .currentStage("publishing")
                        .scheduledPublishAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                        .updateTime(LocalDateTime.of(2026, 5, 1, 10, 5))
                        .requestJson("{\"heavy\":\"yes\"}")
                        .latestContextJson("{\"heavy\":\"yes\"}")
                        .finalResultJson("{\"heavy\":\"yes\"}")
                        .retryPolicyJson("{\"heavy\":\"yes\"}")
                        .build()));
        when(publishRepository.countTask(1001L, "k", "running", "publishing")).thenReturn(11);

        PageResult<XhsPublishTaskPageVO> pageResult = service.pageTask(PageXhsPublishTaskDTO.builder()
                .keyword("k")
                .taskStatus("running")
                .currentStage("publishing")
                .pageNum(2)
                .pageSize(5)
                .build());

        assertEquals(11, pageResult.getTotal());
        assertEquals(2, pageResult.getPageNum());
        assertEquals(3, pageResult.getPageSum());
        assertEquals(1, pageResult.getList().size());
        assertEquals("task-1", pageResult.getList().get(0).getTaskId());
        verify(publishRepository).pageTask(1001L, "k", "running", "publishing", 5, 5);
    }

    @Test
    void shouldKeepDetailFieldsComplete() {
        XhsPublishTaskEntity task = XhsPublishTaskEntity.builder()
                .taskId("task-9")
                .taskName("full-detail")
                .publishMode("B")
                .publishType("image")
                .taskStatus("failed")
                .currentStage("failed_terminal")
                .requestJson("{\"title\":\"A\"}")
                .latestContextJson("{\"title\":\"B\"}")
                .finalResultJson("{\"status\":\"failed\"}")
                .retryPolicyJson("{\"maxRetry\":3}")
                .build();
        when(taskAccessSupport.queryOwnedAggregate("task-9")).thenReturn(XhsPublishTaskAggregate.builder()
                .task(task)
                .attemptList(List.of())
                .reviewList(List.of())
                .assetList(List.of())
                .build());

        XhsPublishTaskDetailVO detailVO = service.detailTask("task-9");
        assertEquals("{\"title\":\"A\"}", detailVO.getRequestJson());
        assertEquals("{\"title\":\"B\"}", detailVO.getLatestContextJson());
        assertEquals("{\"status\":\"failed\"}", detailVO.getFinalResultJson());
        assertEquals("{\"maxRetry\":3}", detailVO.getRetryPolicyJson());
    }

}
