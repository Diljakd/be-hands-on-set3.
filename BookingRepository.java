
	package com.example.meetingbooking.repository;

	import com.example.meetingbooking.entity.Booking;
	import com.example.meetingbooking.entity.BookingStatus;
	import org.springframework.data.jpa.repository.JpaRepository;
	import org.springframework.data.jpa.repository.Query;
	import org.springframework.data.repository.query.Param;
	import org.springframework.stereotype.Repository;
	import org.springframework.data.jpa.repository.EntityGraph;

	import java.time.LocalDateTime;
	import java.util.List;
	import java.util.Optional;

	@Repository
	public interface BookingRepository extends JpaRepository<Booking, Long> {

	    // Find overlapping confirmed bookings in the same room.
	    @Query("""
	        SELECT b FROM Booking b
	        WHERE b.room.id = :roomId
	        AND b.status = :status
	        AND b.startTime < :requestedEnd
	        AND b.endTime > :requestedStart
	        ORDER BY b.startTime
	        """)
	    List<Booking> findOverlappingBookings(
	            @Param("roomId") Long roomId,
	            @Param("status") BookingStatus status,
	            @Param("requestedStart") LocalDateTime requestedStart,
	            @Param("requestedEnd") LocalDateTime requestedEnd);

	    @EntityGraph(attributePaths = {"attendeeEmails", "room"})
	    List<Booking>
	    findByRoomIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
	            Long roomId,
	            LocalDateTime dayStart,
	            LocalDateTime nextDayStart);

	    @EntityGraph(attributePaths = {"attendeeEmails", "room"})
	    Optional<Booking> findWithDetailsById(Long id);

	    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
	}
	
	@EntityGraph(attributePaths = {"attendeeEmails", "room"})
	@Query("""
	    SELECT DISTINCT b
	    FROM Booking b
	    WHERE b.status = :status
	      AND b.startTime < :rangeEnd
	      AND b.endTime > :rangeStart
	    """)
	List<Booking> findActiveBookingsInRange(
	        @Param("status") BookingStatus status,
	        @Param("rangeStart") LocalDateTime rangeStart,
	        @Param("rangeEnd") LocalDateTime rangeEnd
	);
}
