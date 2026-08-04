package com.calio.calendar.integration.service;

import java.util.Collection;

public final class GoogleInboundChangeClassifier {

    public Classification classify(
            String baselineHash,
            String canonicalHash,
            Collection<String> pendingDesiredHashes,
            String googleHash
    ) {
        boolean hasLocalBranch = !canonicalHash.equals(baselineHash)
                || !pendingDesiredHashes.isEmpty();
        if (googleHash.equals(baselineHash)) {
            return hasLocalBranch
                    ? Classification.CALIO_ONLY
                    : Classification.METADATA_ONLY;
        }
        if (googleHash.equals(canonicalHash)
                || pendingDesiredHashes.contains(googleHash)) {
            return Classification.ALREADY_CONVERGED;
        }
        return hasLocalBranch
                ? Classification.TRUE_CONFLICT
                : Classification.GOOGLE_ONLY;
    }

    public Classification classifyDeletion(
            String baselineHash,
            String canonicalHash,
            Collection<String> pendingDesiredHashes
    ) {
        return classifyDeletion(
                baselineHash, canonicalHash, !pendingDesiredHashes.isEmpty()
        );
    }

    public Classification classifyDeletion(
            String baselineHash,
            String canonicalHash,
            boolean hasPendingDesiredSnapshot
    ) {
        boolean hasLocalBranch = !canonicalHash.equals(baselineHash)
                || hasPendingDesiredSnapshot;
        return hasLocalBranch
                ? Classification.TRUE_CONFLICT
                : Classification.GOOGLE_ONLY;
    }

    public enum Classification {
        GOOGLE_ONLY,
        CALIO_ONLY,
        ALREADY_CONVERGED,
        TRUE_CONFLICT,
        METADATA_ONLY
    }
}
