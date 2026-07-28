package com.calio.calendar.groupinvitation.repository;

import java.time.Instant;

public interface InvitationSummaryProjection {

    Long getInvitationId();

    Instant getExpiresAt();
}
