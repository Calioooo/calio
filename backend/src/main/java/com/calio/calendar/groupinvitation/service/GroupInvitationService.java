package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.controller.dto.IssueGroupInvitationResponse;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GroupInvitationService {

    private static final Logger log = LoggerFactory.getLogger(GroupInvitationService.class);
    private static final int MAX_ISSUE_ATTEMPTS = 3;

    private final InvitationCredentialService credentialService;
    private final GroupInvitationCommandService commandService;
    private final TransactionTemplate issueTransaction;

    public GroupInvitationService(
            InvitationCredentialService credentialService,
            GroupInvitationCommandService commandService,
            PlatformTransactionManager transactionManager
    ) {
        this.credentialService = credentialService;
        this.commandService = commandService;
        this.issueTransaction = new TransactionTemplate(transactionManager);
        this.issueTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public IssueGroupInvitationResponse issue(Long accountId, Long groupSpaceId) {
        for (int attempt = 0; attempt < MAX_ISSUE_ATTEMPTS; attempt++) {
            InvitationCredentialPair credentials = credentialService.generatePair();
            try {
                return issueTransaction.execute(
                        status -> commandService.issueOnce(accountId, groupSpaceId, credentials)
                );
            } catch (DataIntegrityViolationException exception) {
                // A failed attempt is fully rolled back by TransactionTemplate.
            }
        }

        log.error("Group invitation generation failed. errorCode={}",
                ErrorCode.GROUP_INVITATION_GENERATION_FAILED.name());
        throw new CalioException(ErrorCode.GROUP_INVITATION_GENERATION_FAILED);
    }
}
