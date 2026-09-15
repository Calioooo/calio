package com.calio.calendar.common.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestExecutionListeners;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestExecutionListeners(
        listeners = SharedIntegrationDatabaseCleanupListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
public @interface SharedIntegrationDatabase {
}
