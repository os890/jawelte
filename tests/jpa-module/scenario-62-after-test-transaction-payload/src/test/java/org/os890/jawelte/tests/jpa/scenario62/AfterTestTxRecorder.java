/*
 * Copyright 2026 os890
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.os890.jawelte.tests.jpa.scenario62;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.core.api.event.AfterTestTransaction;

/**
 * Captures every {@link AfterTestTransaction} event the subject's
 * lifecycle adapter fires, in order. Static state so the outer
 * {@code Scenario62Test} (running in the same JVM but a different
 * CDI container, via {@code EngineTestKit}) can read the recordings
 * back after the kit run completes.
 */
@ApplicationScoped
public class AfterTestTxRecorder {

    /** Recorded events in firing order. Synchronized for thread-safe append. */
    public static final List<RecordedEvent> EVENTS = Collections.synchronizedList(new ArrayList<>());

    /** Default constructor required by CDI. */
    public AfterTestTxRecorder() {
    }

    /** @param event the fired event */
    public void on(@Observes AfterTestTransaction event) {
        EVENTS.add(new RecordedEvent(event.isCommitted(), event.getTestMethodName()));
    }

    /**
     * Snapshot of the {@code AfterTestTransaction} payload at fire
     * time. Equality is value-based — outer-test assertions compare
     * an expected record against the captured one.
     *
     * @param committed      the {@code isCommitted()} flag at fire time
     * @param testMethodName the {@code getTestMethodName()} value at fire time
     */
    public record RecordedEvent(boolean committed, String testMethodName) {
    }
}
