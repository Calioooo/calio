package com.calio.calendar.aicalendar.repository;

import com.calio.calendar.aicalendar.domain.CalendarConversationMessage;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarConversationMessageRepository
        extends JpaRepository<CalendarConversationMessage, Long> {

    List<CalendarConversationMessage> findTop20ByConversation_IdOrderByIdDesc(Long conversationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from CalendarConversationMessage message
            where message.conversation.lastActivityAt < :cutoff
            """)
    int deleteByConversationInactiveBefore(@Param("cutoff") Instant cutoff);
}
