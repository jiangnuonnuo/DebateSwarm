package com.dasi.domain.room.service.debate.arbitration;

import com.dasi.domain.room.model.valobj.ArbitrationDecisionResultVO;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;

/**
 * @Author: xerina
 * @Description: 辩论仲裁调用服务
 */
public interface IDebateArbitrationService {

    ArbitrationDecisionResultVO arbitrate(ArbitrationPromptContextVO promptContext);
}
