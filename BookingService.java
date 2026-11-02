package com.example.meetingbooking.service;

import com.example.meetingbooking.dto.BookingRequest;
import com.example.meetingbooking.dto.BookingResponse;
import java.time.LocalDate;
import java.util.List;

public interface BookingService {

    BookingResponse create(BookingRequest request, String idempotencyKey);

    BookingResponse get(Long id);

    List<BookingResponse> getByRoomAndDate(Long roomId, LocalDate date);

    BookingResponse cancel(Long id);
}