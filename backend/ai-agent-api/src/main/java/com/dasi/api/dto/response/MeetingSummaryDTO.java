package com.dasi.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingSummaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 阶段结论 */
    private String conclusion;

    /** 行动项列表 */
    private List<String> actionItems;

    /** 下一轮触发条件 */
    private String nextRoundCondition;

    /** 输出结论角色 */
    private String concludedByRole;

    /** 结论时间 */
    private String concludedAt;
}
