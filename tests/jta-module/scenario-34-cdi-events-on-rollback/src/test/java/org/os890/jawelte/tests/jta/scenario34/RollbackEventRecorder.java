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
package org.os890.jawelte.tests.jta.scenario34;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.module.jpa.api.event.TransactionBeforeCompletion;
import org.os890.jawelte.module.jpa.api.event.TransactionCommitted;
import org.os890.jawelte.module.jpa.api.event.TransactionRolledBack;
import org.os890.jawelte.module.jpa.api.event.TransactionStarted;

/** Counts the JTA tx events seen during the rollback test. */
@ApplicationScoped
public class RollbackEventRecorder {

    private final AtomicInteger startedCount = new AtomicInteger();

    private final AtomicInteger beforeCompletionCount = new AtomicInteger();

    private final AtomicInteger committedCount = new AtomicInteger();

    private final AtomicInteger rolledBackCount = new AtomicInteger();

    /** Default constructor required by CDI. */
    public RollbackEventRecorder() {
    }

    void onStarted(@Observes TransactionStarted event) {
        startedCount.incrementAndGet();
    }

    void onBeforeCompletion(@Observes TransactionBeforeCompletion event) {
        beforeCompletionCount.incrementAndGet();
    }

    void onCommitted(@Observes TransactionCommitted event) {
        committedCount.incrementAndGet();
    }

    void onRolledBack(@Observes TransactionRolledBack event) {
        rolledBackCount.incrementAndGet();
    }

    /** @return the number of TransactionStarted events seen so far */
    public int startedCount() {
        return startedCount.get();
    }

    /** @return the number of TransactionBeforeCompletion events seen so far */
    public int beforeCompletionCount() {
        return beforeCompletionCount.get();
    }

    /** @return the number of TransactionCommitted events seen so far */
    public int committedCount() {
        return committedCount.get();
    }

    /** @return the number of TransactionRolledBack events seen so far */
    public int rolledBackCount() {
        return rolledBackCount.get();
    }
}
