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
package org.os890.jawelte.tests.jpa.scenario30;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.module.jpa.api.event.TransactionCommitted;
import org.os890.jawelte.module.jpa.api.event.TransactionRolledBack;
import org.os890.jawelte.module.jpa.api.event.TransactionStarted;

/**
 * Counts jpa-module's tx-lifecycle events on the calling thread. Used by
 * {@link Scenario30Test#thirdMethodManualRollbackDiscardsThePersist} to
 * lock in the "framework gets out of the way when callers reach for raw
 * JPA" claim — a test that drives {@code EntityTransaction} directly off
 * an EMF-created {@code EntityManager} must NOT fire any of jpa-module's
 * tx events, because that path bypasses the strategy and the interceptors.
 */
@ApplicationScoped
public class TxEventRecorder {

    private final AtomicInteger started = new AtomicInteger();
    private final AtomicInteger committed = new AtomicInteger();
    private final AtomicInteger rolledBack = new AtomicInteger();

    /** Default constructor required by CDI. */
    public TxEventRecorder() {
    }

    /**
     * Increment the started counter.
     *
     * @param event the event payload (PU name unused here)
     */
    public void onStarted(@Observes TransactionStarted event) {
        started.incrementAndGet();
    }

    /**
     * Increment the committed counter.
     *
     * @param event the event payload
     */
    public void onCommitted(@Observes TransactionCommitted event) {
        committed.incrementAndGet();
    }

    /**
     * Increment the rolled-back counter.
     *
     * @param event the event payload
     */
    public void onRolledBack(@Observes TransactionRolledBack event) {
        rolledBack.incrementAndGet();
    }

    /** @return current TransactionStarted count */
    public int started() {
        return started.get();
    }

    /** @return current TransactionCommitted count */
    public int committed() {
        return committed.get();
    }

    /** @return current TransactionRolledBack count */
    public int rolledBack() {
        return rolledBack.get();
    }

    /** Reset all counters to zero. */
    public void reset() {
        started.set(0);
        committed.set(0);
        rolledBack.set(0);
    }
}
