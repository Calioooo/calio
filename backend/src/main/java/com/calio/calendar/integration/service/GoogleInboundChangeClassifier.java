package com.calio.calendar.integration.service;

import java.util.Collection;

public final class GoogleInboundChangeClassifier {

    public Classification classify(
            String baselineHash,
            String canonicalHash,
            Collection<String> pendingDesiredHashes,
            String googleHash
    ) {
        if (googleHash.equals(baselineHash)) {
            return pendingDesiredHashes.isEmpty()
                    ? Classification.METADATA_ONLY
                    : Classification.CALIO_ONLY;
        }
        if (googleHash.equals(canonicalHash) || pendingDesiredHashes.contains(googleHash)) {
            return Classification.ALREADY_CONVERGED;
        }
        return pendingDesiredHashes.isEmpty()
                ? Classification.GOOGLE_ONLY
                : Classification.TRUE_CONFLICT;
    }

    public enum Classification {
        GOOGLE_ONLY,
        CALIO_ONLY,
        ALREADY_CONVERGED,
        TRUE_CONFLICT,
        METADATA_ONLY
    }
}
