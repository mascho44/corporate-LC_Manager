package de.ostms.lc.calendarfeed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface CalendarFeedRepository extends JpaRepository<CalendarFeed,UUID> {
 Optional<CalendarFeed> findByTokenHash(String tokenHash);
 @Query("select f from CalendarFeed f where f.tenantId=:tenantId and lower(f.username)=lower(:username)") Optional<CalendarFeed> forUser(@Param("tenantId") UUID tenantId,@Param("username") String username);
}
