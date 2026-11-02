package com.example.meetingbooking.service.impl;

import com.example.meetingbooking.dto.RoomRequest;
import com.example.meetingbooking.dto.RoomResponse;
import com.example.meetingbooking.entity.Room;
import com.example.meetingbooking.exception.ResourceNotFoundException;
import com.example.meetingbooking.repository.RoomRepository;
import com.example.meetingbooking.service.RoomService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;

    public RoomServiceImpl(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    public RoomResponse create(RoomRequest request) {

        if (roomRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new IllegalArgumentException("Room name already exists");
        }

        Room room = new Room(
                request.name().trim(),
                request.capacity(),
                request.active()
        );

        Room savedRoom = roomRepository.save(room);

        return toResponse(savedRoom);
    }

    @Override
    public RoomResponse update(Long id, RoomRequest request) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Room not found"));

        room.setName(request.name().trim());
        room.setCapacity(request.capacity());
        room.setActive(request.active());

        return toResponse(room);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse get(Long id) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Room not found"));

        return toResponse(room);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getAll() {

        return roomRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public void delete(Long id) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Room not found"));

        roomRepository.delete(room);
    }

    private RoomResponse toResponse(Room room) {

        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getCapacity(),
                room.isActive()
        );
    }
}