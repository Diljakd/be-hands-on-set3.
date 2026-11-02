package com.example.meetingbooking.controller;

import com.example.meetingbooking.dto.*;
import com.example.meetingbooking.service.FreeSlotService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/free-slots")
public class FreeSlotController {

    private final FreeSlotService freeSlotService;

    public FreeSlotController(FreeSlotService freeSlotService) {
        this.freeSlotService = freeSlotService;
    }

    @PostMapping
    public List<FreeSlotResponse> findFreeSlots(
            @Valid @RequestBody FreeSlotRequest request) {

        return freeSlotService.findFreeSlots(request);
    }
}