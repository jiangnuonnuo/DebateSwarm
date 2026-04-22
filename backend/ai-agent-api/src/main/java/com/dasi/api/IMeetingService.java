package com.dasi.api;

import com.dasi.api.dto.request.MeetingActionRequest;
import com.dasi.api.dto.request.MeetingCreateRequest;
import com.dasi.api.dto.request.MeetingStartRequest;
import com.dasi.api.dto.response.MeetingActionDTO;
import com.dasi.api.dto.response.MeetingStatusDTO;
import com.dasi.types.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 真实会议模式 V1 - 后端接口定义
 */
public interface IMeetingService {

    @PostMapping("/chat-room/meeting/create")
    Result<String> createMeeting(@RequestBody MeetingCreateRequest request);

    @PostMapping("/chat-room/meeting/start")
    Result<Boolean> startMeeting(@RequestBody MeetingStartRequest request);

    @PostMapping("/chat-room/meeting/action")
    Result<Boolean> applyAction(@RequestBody MeetingActionRequest request);

    @GetMapping("/chat-room/meeting/status")
    Result<MeetingStatusDTO> queryMeetingStatus(@RequestParam("roomId") String roomId,
                                                @RequestParam(value = "meetingId", required = false) String meetingId);

    @GetMapping("/chat-room/meeting/timeline")
    Result<List<MeetingActionDTO>> queryMeetingTimeline(@RequestParam("roomId") String roomId,
                                                        @RequestParam(value = "meetingId", required = false) String meetingId);
}

