package com.calio.calendar.aicalendar.repository;

import com.calio.calendar.aicalendar.domain.CalendarConversation;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarConversationRepository extends JpaRepository<CalendarConversation, Long> {

    Optional<CalendarConversation> findByConversationIdAndAccount_Id(String conversationId, Long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CalendarConversation conversation where conversation.lastActivityAt < :cutoff")
    int deleteInactiveBefore(@Param("cutoff") Instant cutoff);
}
