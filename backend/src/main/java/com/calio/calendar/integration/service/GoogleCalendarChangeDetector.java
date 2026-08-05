package com.calio.calendar.integration.service;

import java.util.Collection;

public final class GoogleCalendarChangeDetector {

    public ChangeType detectUpdate(
            String lastSyncedContentHash,
            String currentCalioContentHash,
            Collection<String> pendingGoogleWriteContentHashes,
            String currentGoogleContentHash
    ) {
        boolean calioChangedSinceLastSync =
                !currentCalioContentHash.equals(lastSyncedContentHash)
                        || !pendingGoogleWriteContentHashes.isEmpty();
        if (currentGoogleContentHash.equals(lastSyncedContentHash)) {
            return calioChangedSinceLastSync
                    ? ChangeType.CALIO_CHANGED
                    : ChangeType.CONTENT_UNCHANGED;
        }
        if (currentGoogleContentHash.equals(currentCalioContentHash)
                || pendingGoogleWriteContentHashes.contains(currentGoogleContentHash)) {
            return ChangeType.CONTENT_ALREADY_MATCHES;
        }
        return calioChangedSinceLastSync
                ? ChangeType.CALIO_AND_GOOGLE_CHANGED
                : ChangeType.GOOGLE_CHANGED;
    }

    public ChangeType detectDeletion(
            String lastSyncedContentHash,
            String currentCalioContentHash,
            Collection<String> pendingGoogleWriteContentHashes
    ) {
        return detectDeletion(
                lastSyncedContentHash,
                currentCalioContentHash,
                !pendingGoogleWriteContentHashes.isEmpty()
        );
    }

    public ChangeType detectDeletion(
            String lastSyncedContentHash,
            String currentCalioContentHash,
            boolean hasPendingGoogleWrite
    ) {
        boolean calioChangedSinceLastSync =
                !currentCalioContentHash.equals(lastSyncedContentHash)
                        || hasPendingGoogleWrite;
        return calioChangedSinceLastSync
                ? ChangeType.CALIO_AND_GOOGLE_CHANGED
                : ChangeType.GOOGLE_CHANGED;
    }

    public enum ChangeType {
        GOOGLE_CHANGED,
        CALIO_CHANGED,
        CONTENT_ALREADY_MATCHES,
        CALIO_AND_GOOGLE_CHANGED,
        CONTENT_UNCHANGED
    }
}
