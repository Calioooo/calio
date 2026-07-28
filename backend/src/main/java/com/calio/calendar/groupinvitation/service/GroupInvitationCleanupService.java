package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GroupInvitationCleanupService {

    private final GroupInvitationRepository invitationRepository;
    private final GroupInvitationProperties properties;
    private final TransactionTemplate cleanupTransaction;

    public GroupInvitationCleanupService(
            GroupInvitationRepository invitationRepository,
            GroupInvitationProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.invitationRepository = invitationRepository;
        this.properties = properties;
        this.cleanupTransaction = new TransactionTemplate(transactionManager);
        this.cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int deleteBatch(Instant cutoff) {
        Integer deletedCount = cleanupTransaction.execute(status -> {
            List<GroupInvitation> invitations = invitationRepository.findCleanupBatch(
                    cutoff,
                    PageRequest.of(0, properties.getCleanupBatchSize())
            );
            invitationRepository.deleteAllInBatch(invitations);
            return invitations.size();
        });
        return deletedCount == null ? 0 : deletedCount;
    }
}
