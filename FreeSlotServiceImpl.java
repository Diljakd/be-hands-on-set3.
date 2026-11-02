package com.example.meetingbooking.service.impl;

import com.example.meetingbooking.dto.*;
import com.example.meetingbooking.entity.*;
import com.example.meetingbooking.repository.*;
import com.example.meetingbooking.service.FreeSlotService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class FreeSlotServiceImpl implements FreeSlotService {

    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    public FreeSlotServiceImpl(
            BookingRepository bookingRepository,
            RoomRepository roomRepository) {

        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FreeSlotResponse> findFreeSlots(FreeSlotRequest request) {

        LocalDateTime dayStart =
                request.date().atTime(WORK_START);

        LocalDateTime dayEnd =
                request.date().atTime(WORK_END);

        // Step 1: Normalize attendee email addresses.
        Set<String> attendees = new HashSet<>();

        request.attendeeEmails().stream()
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .forEach(attendees::add);

        // Step 2: Fetch all confirmed bookings across every room.
        List<Booking> bookings =
                bookingRepository.findActiveBookingsInRange(
                        BookingStatus.CONFIRMED,
                        dayStart,
                        dayEnd
                );

        // Step 3: Collect bookings that involve requested attendees.
        List<Interval> busyIntervals = new ArrayList<>();

        for (Booking booking : bookings) {

            boolean attendeeIsBusy =
                    booking.getAttendeeEmails().stream()
                            .map(email -> email.toLowerCase(Locale.ROOT))
                            .anyMatch(attendees::contains);

            if (attendeeIsBusy) {

                busyIntervals.add(
                        clipped(
                                booking.getStartTime(),
                                booking.getEndTime(),
                                dayStart,
                                dayEnd
                        )
                );
            }
        }

        // Step 4: Sort and merge overlapping busy intervals.
        List<Interval> mergedBusy =
                mergeIntervals(busyIntervals);

        // Step 5: Find the gaps between busy intervals.
        List<Interval> freeIntervals =
                findFreeIntervals(mergedBusy, dayStart, dayEnd);

        // Step 6: Fetch active rooms.
        List<Room> activeRooms =
                roomRepository.findByActiveTrue();

        List<FreeSlotResponse> result = new ArrayList<>();

        // Step 7: Check minimum duration and room availability.
        for (Interval slot : freeIntervals) {

            long duration = Duration.between(
                    slot.start(), slot.end()).toMinutes();

            if (duration < request.minimumDurationMinutes()) {
                continue;
            }

            List<RoomResponse> availableRooms =
                    activeRooms.stream()

                            // Room must accommodate all attendees.
                            .filter(room ->
                                    room.getCapacity() >= attendees.size())

                            // Room must be free for the entire slot.
                            .filter(room ->
                                    bookings.stream().noneMatch(booking ->

                                            booking.getRoom().getId()
                                                    .equals(room.getId())

                                            && booking.getStatus()
                                                    == BookingStatus.CONFIRMED

                                            && overlaps(
                                                    booking.getStartTime(),
                                                    booking.getEndTime(),
                                                    slot.start(),
                                                    slot.end()
                                            )
                                    )
                            )

                            .map(room -> new RoomResponse(
                                    room.getId(),
                                    room.getName(),
                                    room.getCapacity(),
                                    room.isActive()
                            ))

                            .toList();

            result.add(new FreeSlotResponse(
                    slot.start(),
                    slot.end(),
                    availableRooms
            ));
        }

        return result;
    }

    // Restrict booking intervals to 09:00–18:00.
    private Interval clipped(
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {

        return new Interval(
                start.isBefore(rangeStart) ? rangeStart : start,
                end.isAfter(rangeEnd) ? rangeEnd : end
        );
    }

    // Merge overlapping or touching intervals.
    private List<Interval> mergeIntervals(List<Interval> intervals) {

        if (intervals.isEmpty()) {
            return List.of();
        }

        List<Interval> sorted = intervals.stream()
                .filter(i -> i.start().isBefore(i.end()))
                .sorted(Comparator.comparing(Interval::start))
                .toList();

        List<Interval> merged = new ArrayList<>();

        Interval current = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {

            Interval next = sorted.get(i);

            if (!next.start().isAfter(current.end())) {

                LocalDateTime maxEnd =
                        next.end().isAfter(current.end())
                                ? next.end()
                                : current.end();

                current = new Interval(current.start(), maxEnd);

            } else {

                merged.add(current);
                current = next;
            }
        }

        merged.add(current);

        return merged;
    }

    // Find free time between merged busy intervals.
    private List<Interval> findFreeIntervals(
            List<Interval> busy,
            LocalDateTime dayStart,
            LocalDateTime dayEnd) {

        List<Interval> free = new ArrayList<>();

        LocalDateTime cursor = dayStart;

        for (Interval interval : busy) {

            if (cursor.isBefore(interval.start())) {

                free.add(new Interval(
                        cursor,
                        interval.start()
                ));
            }

            if (interval.end().isAfter(cursor)) {
                cursor = interval.end();
            }
        }

        if (cursor.isBefore(dayEnd)) {
            free.add(new Interval(cursor, dayEnd));
        }

        return free;
    }

    // Check whether two intervals overlap.
    private boolean overlaps(
            LocalDateTime aStart,
            LocalDateTime aEnd,
            LocalDateTime bStart,
            LocalDateTime bEnd) {

        return aStart.isBefore(bEnd)
                && aEnd.isAfter(bStart);
    }

    private record Interval(
            LocalDateTime start,
            LocalDateTime end) {
    }
}