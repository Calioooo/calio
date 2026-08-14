package com.calio.calendar.integration.sync.page;

import java.util.Collection;

/** Classifies provider content against the last common baseline and the local branch. */
public final class GoogleCalendarInboundChangeClassifier {

    public Change classify(
            String syncedContentHash,
            String canonicalContentHash,
            Collection<String> pendingDesiredContentHashes,
            String providerContentHash
    ) {
        boolean localBranchExists = !canonicalContentHash.equals(syncedContentHash)
                || !pendingDesiredContentHashes.isEmpty();

        if (providerContentHash.equals(canonicalContentHash)
                || pendingDesiredContentHashes.contains(providerContentHash)) {
            return Change.ALREADY_CONVERGED;
        }
        if (providerContentHash.equals(syncedContentHash)) {
            return localBranchExists ? Change.CALIO_ONLY : Change.METADATA_ONLY;
        }
        return localBranchExists ? Change.TRUE_CONFLICT : Change.GOOGLE_ONLY;
    }

    public enum Change {
        GOOGLE_ONLY,
        CALIO_ONLY,
        ALREADY_CONVERGED,
        METADATA_ONLY,
        TRUE_CONFLICT
    }
}
