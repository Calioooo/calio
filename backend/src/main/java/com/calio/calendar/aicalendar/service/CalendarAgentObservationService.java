package com.calio.calendar.aicalendar.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CalendarAgentObservationService {

    private static final Logger log = LoggerFactory.getLogger(CalendarAgentObservationService.class);

    private final MeterRegistry meterRegistry;

    public CalendarAgentObservationService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRun(
            String conversationId,
            String model,
            String outcome,
            Duration latency,
            String errorCode
    ) {
        log.info(
                "AI calendar agent run finished. conversationId={} model={} outcome={} latencyMs={} errorCode={}",
                conversationId,
                model,
                outcome,
                latency.toMillis(),
                errorCode
        );
        meterRegistry.timer("ai.calendar.agent.run", "outcome", outcome).record(latency);
    }

    public void recordTool(
            String conversationId,
            String toolName,
            String outcome,
            Duration latency,
            int resultCount
    ) {
        log.info(
                "AI calendar tool finished. conversationId={} tool={} version=1 outcome={} latencyMs={} resultCount={}",
                conversationId,
                toolName,
                outcome,
                latency.toMillis(),
                resultCount
        );
        meterRegistry.timer("ai.calendar.agent.tool", "tool", toolName, "outcome", outcome)
                .record(latency);
    }
}
