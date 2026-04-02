package com.dasi.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateRoomRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String roomId;
}
