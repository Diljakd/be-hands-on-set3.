package com.example.meetingbooking.service.impl;

import com.example.meetingbooking.dto.BookingRequest;
import com.example.meetingbooking.dto.BookingResponse;
import com.example.meetingbooking.entity.*;
import com.example.meetingbooking.exception.BookingConflictException;
import com.example.meetingbooking.exception.ResourceNotFoundException;
import com.example.meetingbooking.repository.BookingRepository;
import com.example.meetingbooking.repository.RoomRepository;
import com.example.meetingbooking.service.BookingService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class BookingServiceImpl implements BookingService {

    private static final LocalTime WORK_START =
            LocalTime.of(9, 0);

    private static final LocalTime WORK_END =
            LocalTime.of(18, 0);

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            RoomRepository roomRepository) {

        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public BookingResponse create(
            BookingRequest request,
            String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key header is required");
        }

        Set<String> attendees = normalizeAttendees(request);

        validateBooking(request, attendees);

        // Check whether this request was already processed.
        Optional<Booking> previous =
                bookingRepository.findByIdempotencyKey(idempotencyKey);

        if (previous.isPresent()) {
            return replayOrReject(
                    previous.get(), request, attendees);
        }

        // Lock room to prevent concurrent overlapping bookings.
        Room room = roomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Room not found"));

        if (!room.isActive()) {
            throw new IllegalArgumentException("Room is not active");
        }

        if (attendees.size() > room.getCapacity()) {
            throw new IllegalArgumentException(
                    "Attendee count exceeds room capacity");
        }

        // Check idempotency again after acquiring the lock.
        previous = bookingRepository.findByIdempotencyKey(idempotencyKey);

        if (previous.isPresent()) {
            return replayOrReject(
                    previous.get(), request, attendees);
        }

        // Check for overlapping bookings.
        List<Booking> clashes =
                bookingRepository.findOverlappingBookings(
                        room.getId(),
                        BookingStatus.CONFIRMED,
                        request.startTime(),
                        request.endTime()
                );

        if (!clashes.isEmpty()) {
            throw new BookingConflictException(
                    "Booking overlaps with existing booking ID "
                            + clashes.get(0).getId());
        }

        Booking booking = new Booking(
                room,
                request.title().trim(),
                normalizeEmail(request.organiserEmail()),
                attendees,
                request.startTime(),
                request.endTime(),
                idempotencyKey
        );

        Booking saved = bookingRepository.saveAndFlush(booking);

        return toResponse(saved);
    }

    private void validateBooking(
            BookingRequest request,
            Set<String> attendees) {

        LocalDateTime start = request.startTime();
        LocalDateTime end = request.endTime();

        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new IllegalArgumentException(
                    "Booking must be on the same day");
        }

        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "Start time must be before end time");
        }

        if (start.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Booking cannot be in the past");
        }

        if (start.toLocalTime().isBefore(WORK_START)
                || end.toLocalTime().isAfter(WORK_END)) {
            throw new IllegalArgumentException(
                    "Booking must be between 09:00 and 18:00");
        }

        if (start.getMinute() % 15 != 0
                || end.getMinute() % 15 != 0
                || start.getSecond() != 0
                || end.getSecond() != 0) {
            throw new IllegalArgumentException(
                    "Booking must use 15-minute time intervals");
        }

        if (Duration.between(start, end)
                .compareTo(Duration.ofHours(4)) > 0) {
            throw new IllegalArgumentException(
                    "Booking duration cannot exceed 4 hours");
        }

        if (attendees.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one attendee is required");
        }
    }

    private Set<String> normalizeAttendees(BookingRequest request) {

        Set<String> attendees = new TreeSet<>();

        if (request.attendeeEmails() != null) {
            request.attendeeEmails().stream()
                    .filter(Objects::nonNull)
                    .map(this::normalizeEmail)
                    .forEach(attendees::add);
        }

        // Organiser must always be an attendee.
        if (request.organiserEmail() != null
                && !request.organiserEmail().isBlank()) {
            attendees.add(normalizeEmail(request.organiserEmail()));
        }

        return attendees;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private BookingResponse replayOrReject(
            Booking existing,
            BookingRequest request,
            Set<String> attendees) {

        boolean sameRequest =
                existing.getRoom().getId().equals(request.roomId())
                && existing.getTitle().equals(request.title().trim())
                && existing.getOrganiserEmail().equals(
                        normalizeEmail(request.organiserEmail()))
                && existing.getStartTime().equals(request.startTime())
                && existing.getEndTime().equals(request.endTime())
                && existing.getAttendeeEmails().equals(attendees);

        if (!sameRequest) {
            throw new BookingConflictException(
                    "Idempotency key was used for another request");
        }

        return toResponse(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse get(Long id) {

        Booking booking = bookingRepository.findWithDetailsById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Booking not found"));

        return toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getByRoomAndDate(
            Long roomId, LocalDate date) {

        if (!roomRepository.existsById(roomId)) {
            throw new ResourceNotFoundException("Room not found");
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime nextDay = date.plusDays(1).atStartOfDay();

        return bookingRepository
                .findByRoomIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        roomId, start, nextDay)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public BookingResponse cancel(Long id) {

        Booking booking = bookingRepository.findWithDetailsById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Booking not found"));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return toResponse(booking);
        }

        roomRepository.findByIdForUpdate(booking.getRoom().getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Room not found"));

        booking.cancel();

        return toResponse(booking);
    }

    private BookingResponse toResponse(Booking booking) {

        return new BookingResponse(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getRoom().getName(),
                booking.getTitle(),
                booking.getOrganiserEmail(),
                Set.copyOf(booking.getAttendeeEmails()),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getCreatedAt(),
                booking.getStatus().name()
        );
    }
}