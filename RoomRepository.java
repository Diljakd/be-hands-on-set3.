
	package Repository;

	import com.example.meetingbooking.entity.Room;
	import org.springframework.data.jpa.repository.JpaRepository;
	import org.springframework.data.jpa.repository.Lock;
	import org.springframework.data.jpa.repository.Query;
	import org.springframework.data.repository.query.Param;
	import jakarta.persistence.LockModeType;
	import java.util.List;
	import java.util.Optional;

	public interface RoomRepository extends JpaRepository<Room, Long> {

	    boolean existsByNameIgnoreCase(String name);

	    List<Room> findByActiveTrue();

	    // Lock the room during booking to prevent simultaneous overlapping bookings.
	    @Lock(LockModeType.PESSIMISTIC_WRITE)
	    @Query("SELECT r FROM Room r WHERE r.id = :id")
	    Optional<Room> findByIdForUpdate(@Param("id") Long id);
	}
}
