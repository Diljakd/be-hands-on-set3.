package com.example.meetingbooking.service;

import com.example.meetingbooking.dto.FreeSlotRequest;
import com.example.meetingbooking.dto.FreeSlotResponse;
import java.util.List;

public interface FreeSlotService {

    List<FreeSlotResponse> findFreeSlots(FreeSlotRequest request);
}