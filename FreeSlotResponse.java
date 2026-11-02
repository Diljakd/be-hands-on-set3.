package com.example.meetingbooking.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FreeSlotResponse(
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<RoomResponse> availableRooms
) {
}