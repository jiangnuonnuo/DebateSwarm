package com.dasi.api;

import com.dasi.api.dto.request.ChatRoomCreateRequest;
import com.dasi.api.dto.request.ChatRoomCursorRequest;
import com.dasi.api.dto.request.ChatRoomMemberRequest;
import com.dasi.api.dto.request.ArbitratorSetRequest;
import com.dasi.api.dto.request.DebateRoomRequest;
import com.dasi.api.dto.request.DebateStartRequest;
import com.dasi.api.dto.request.DebateWinnerRequest;
import com.dasi.api.dto.response.ChatRoomDTO;
import com.dasi.api.dto.response.ChatRoomMemberDTO;
import com.dasi.api.dto.response.ChatRoomMessageDTO;
import com.dasi.api.dto.response.DebateStatusDTO;
import com.dasi.types.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.api
 * @Author: xerina
 * @CreateTime: 2026-03-24  17:54
 * @Description: 聊天室管理 API 接口
 */
public interface IChatRoomService {

    /**
     * 创建聊天室
     * @param request 创建请求
     * @return 房间ID
     */
    @PostMapping("/chat-room/create")
    Result<String> createRoom(@RequestBody ChatRoomCreateRequest request);

    /**
     * 删除聊天室 (逻辑删除)
     * @param roomId 房间ID
     * @return 是否成功
     */
    @DeleteMapping("/chat-room/delete")
    Result<Boolean> deleteRoom(@RequestParam("roomId") String roomId);

    /**
     * 添加成员 (用户或智能体)
     * @param request 成员请求
     * @return 是否成功
     */
    @PostMapping("/chat-room/member/add")
    Result<Boolean> addMember(@RequestBody ChatRoomMemberRequest request);

    /**
     * 移除成员
     * @param request 成员请求
     * @return 是否成功
     */
    @PostMapping("/chat-room/member/remove")
    Result<Boolean> removeMember(@RequestBody ChatRoomMemberRequest request);

    /**
     * 查询房间成员列表
     * @param roomId 房间ID
     * @return 成员列表
     */
    @GetMapping("/chat-room/member/list")
    Result<List<ChatRoomMemberDTO>> queryMemberList(@RequestParam("roomId") String roomId);

    /**
     * 查询用户参与的聊天室列表
     * @return 房间列表
     */
    @GetMapping("/chat-room/list")
    Result<List<ChatRoomDTO>> queryRoomList();

    /**
     * 游标分页查询聊天记录
     * @param request 游标请求
     * @return 消息列表
     */
    @PostMapping("/chat-room/message/cursor")
    Result<List<ChatRoomMessageDTO>> queryMessagesByCursor(@RequestBody ChatRoomCursorRequest request);

    @PostMapping("/chat-room/arbitrator/set")
    Result<Boolean> setArbitrator(@RequestBody ArbitratorSetRequest request);

    @DeleteMapping("/chat-room/arbitrator/remove")
    Result<Boolean> removeArbitrator(@RequestParam("roomId") String roomId);

    @PostMapping("/chat-room/debate/start")
    Result<String> startDebate(@RequestBody DebateStartRequest request);

    @PostMapping("/chat-room/debate/round/winner")
    Result<Boolean> declareRoundWinner(@RequestBody DebateWinnerRequest request);

    @PostMapping("/chat-room/debate/round/next")
    Result<Boolean> startNextRound(@RequestBody DebateRoomRequest request);

    @PostMapping("/chat-room/debate/stop")
    Result<Boolean> stopDebate(@RequestBody DebateRoomRequest request);

    @GetMapping("/chat-room/debate/status")
    Result<DebateStatusDTO> queryDebateStatus(@RequestParam("roomId") String roomId);

}
