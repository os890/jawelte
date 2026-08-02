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
package org.os890.jawelte.tests.jta.scenario58;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.module.jpa.api.event.TransactionBeforeCompletion;
import org.os890.jawelte.module.jpa.api.event.TransactionCommitted;
import org.os890.jawelte.module.jpa.api.event.TransactionRolledBack;
import org.os890.jawelte.module.jpa.api.event.TransactionStarted;

/**
 * Counts the JTA lifecycle events seen during the test <em>and</em>
 * records the name of the thread each observer ran on. The thread name
 * is what makes the assertion meaningful here: the events fired from
 * the JTA {@code Synchronization} callbacks must arrive even though
 * those callbacks run on the thread that completes the transaction,
 * not the one that registered the synchronization.
 *
 * <p>Fields are atomic because the observers genuinely run on two
 * different threads in this scenario.
 */
@ApplicationScoped
public class LifecycleEventThreadRecorder {

    private final AtomicInteger startedCount = new AtomicInteger();

    private final AtomicInteger beforeCompletionCount = new AtomicInteger();

    private final AtomicInteger committedCount = new AtomicInteger();

    private final AtomicInteger rolledBackCount = new AtomicInteger();

    private final AtomicReference<String> startedThreadName = new AtomicReference<>();

    private final AtomicReference<String> beforeCompletionThreadName = new AtomicReference<>();

    private final AtomicReference<String> committedThreadName = new AtomicReference<>();

    /** Default constructor required by CDI. */
    public LifecycleEventThreadRecorder() {
    }

    void onStarted(@Observes TransactionStarted event) {
        startedCount.incrementAndGet();
        startedThreadName.set(Thread.currentThread().getName());
    }

    void onBeforeCompletion(@Observes TransactionBeforeCompletion event) {
        beforeCompletionCount.incrementAndGet();
        beforeCompletionThreadName.set(Thread.currentThread().getName());
    }

    void onCommitted(@Observes TransactionCommitted event) {
        committedCount.incrementAndGet();
        committedThreadName.set(Thread.currentThread().getName());
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

    /** @return the thread that observed TransactionStarted, or {@code null} */
    public String startedThreadName() {
        return startedThreadName.get();
    }

    /** @return the thread that observed TransactionBeforeCompletion, or {@code null} */
    public String beforeCompletionThreadName() {
        return beforeCompletionThreadName.get();
    }

    /** @return the thread that observed TransactionCommitted, or {@code null} */
    public String committedThreadName() {
        return committedThreadName.get();
    }
}
