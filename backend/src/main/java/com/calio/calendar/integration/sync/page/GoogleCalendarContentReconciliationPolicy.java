package com.calio.calendar.integration.sync.page;

import java.util.Collection;
import org.springframework.stereotype.Component;

/** Decides how to reconcile provider content with the current Calio branch. */
@Component
public final class GoogleCalendarContentReconciliationPolicy {

    public GoogleCalendarContentReconciliationDecision decide(
            String syncedContentHash,
            String canonicalContentHash,
            Collection<String> pendingTargetContentHashes,
            String providerContentHash
    ) {
        boolean localBranchExists = !canonicalContentHash.equals(syncedContentHash)
                || !pendingTargetContentHashes.isEmpty();

        if (providerContentHash.equals(canonicalContentHash)
                || pendingTargetContentHashes.contains(providerContentHash)) {
            return GoogleCalendarContentReconciliationDecision.ALREADY_CONVERGED;
        }
        if (providerContentHash.equals(syncedContentHash)) {
            return GoogleCalendarContentReconciliationDecision.CALIO_ONLY;
        }
        return localBranchExists
                ? GoogleCalendarContentReconciliationDecision.TRUE_CONFLICT
                : GoogleCalendarContentReconciliationDecision.GOOGLE_ONLY;
    }
}
