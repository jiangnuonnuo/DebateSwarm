package com.dasi.domain.room.service.debate;

import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.DebateStatusVO;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.service.dispatch.DispatchContext;

import java.util.List;

public interface IDebateService {

    void setArbitrator(String roomId, String clientId);

    void removeArbitrator(String roomId);

    String startDebate(String roomId, String topic, List<String> proClientIds, List<String> conClientIds, Integer turnsPerRound);

    void declareRoundWinner(String roomId, String winnerSide);

    void startNextRound(String roomId);

    void stopDebate(String roomId);

    DebateStatusVO queryDebateStatus(String roomId);

    DispatchDecisionVO decideNextDispatch(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext);
}
