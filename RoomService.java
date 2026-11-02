package com.example.meetingbooking.service;

import com.example.meetingbooking.dto.RoomRequest;
import com.example.meetingbooking.dto.RoomResponse;
import java.util.List;

public interface RoomService {

    RoomResponse create(RoomRequest request);

    RoomResponse update(Long id, RoomRequest request);

    RoomResponse get(Long id);

    List<RoomResponse> getAll();

    void delete(Long id);
}