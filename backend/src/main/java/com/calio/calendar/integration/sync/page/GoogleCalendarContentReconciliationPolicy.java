package com.calio.calendar.integration.sync.page;

import java.util.Collection;

/** Decides how to reconcile provider content with the current Calio branch. */
public final class GoogleCalendarContentReconciliationPolicy {

    public GoogleCalendarContentReconciliationDecision decide(
            String syncedContentHash,
            String canonicalContentHash,
            Collection<String> pendingDesiredContentHashes,
            String providerContentHash
    ) {
        boolean localBranchExists = !canonicalContentHash.equals(syncedContentHash)
                || !pendingDesiredContentHashes.isEmpty();

        if (providerContentHash.equals(canonicalContentHash)
                || pendingDesiredContentHashes.contains(providerContentHash)) {
            return GoogleCalendarContentReconciliationDecision.ALREADY_CONVERGED;
        }
        if (providerContentHash.equals(syncedContentHash)) {
            return localBranchExists
                    ? GoogleCalendarContentReconciliationDecision.CALIO_ONLY
                    : GoogleCalendarContentReconciliationDecision.METADATA_ONLY;
        }
        return localBranchExists
                ? GoogleCalendarContentReconciliationDecision.TRUE_CONFLICT
                : GoogleCalendarContentReconciliationDecision.GOOGLE_ONLY;
    }
}
