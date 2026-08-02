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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;

/**
 * The sync-driven lifecycle events must reach their CDI observers even
 * when the JTA transaction is completed on a thread other than the one
 * that registered the {@code Synchronization}.
 *
 * <p>{@code JtaTransactionStrategy.bindLifecycleEventsToCurrentTransaction()}
 * is the path taken when a vendor {@code @Transactional} interceptor
 * drives the transaction via {@code UserTransaction} instead of the
 * strategy's own {@code begin()} / {@code commit()}: it fires
 * {@code TransactionStarted} inline and registers a
 * {@code Synchronization} that fires the remaining events from
 * {@code beforeCompletion()} / {@code afterCompletion(int)}. JTA runs
 * those callbacks on whichever thread completes the transaction, and
 * that thread need not be the registering one — a transaction may be
 * suspended on one thread and resumed on another.
 *
 * <p>This scenario reproduces exactly that: the transaction is begun and
 * bound on the test thread, suspended, then resumed and committed on a
 * separate, explicitly named thread. Because the strategy captures the
 * {@code BeanManager} at registration time rather than resolving
 * {@code CDI.current()} inside the callback, the events still reach the
 * observers — and the recorded thread names prove the callbacks really
 * did run off the registering thread rather than the assertion passing
 * for the trivial same-thread reason.
 *
 * <p>Runs under the whole portability matrix, so it also confirms the
 * suspend-on-one-thread / complete-on-another shape behaves consistently
 * across the supported transaction managers.
 */
@EnableTestBeans
public class Scenario58Test {

    private static final String COMPLETION_THREAD_NAME = "scenario58-foreign-completion-thread";

    private static final long COMPLETION_TIMEOUT_MILLIS = 30_000L;

    @Inject
    private LifecycleEventThreadRecorder recorder;

    /** No-arg constructor for CDI. */
    public Scenario58Test() {
    }

    @Test
    public void lifecycleEventsFireWhenTheTransactionCompletesOnAnotherThread() throws Exception {
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        TransactionManager transactionManager = strategy.getTransactionManager();
        assertThat(transactionManager)
                .as("the JTA strategy must expose a TransactionManager under this profile")
                .isNotNull();

        String registrationThreadName = Thread.currentThread().getName();

        // Drive the TM directly (not strategy.begin()) so the tx is NOT
        // marked as event-bound — this is the shape a vendor
        // @Transactional interceptor produces, and it is the only path
        // that goes through the Synchronization.
        transactionManager.begin();
        strategy.bindLifecycleEventsToCurrentTransaction();

        assertThat(recorder.startedCount())
                .as("TransactionStarted fires inline on the registering thread")
                .isEqualTo(1);
        assertThat(recorder.startedThreadName())
                .as("TransactionStarted must be observed on the registering thread")
                .isEqualTo(registrationThreadName);
        assertThat(recorder.beforeCompletionCount())
                .as("no completion event may fire before the tx completes")
                .isZero();
        assertThat(recorder.committedCount()).isZero();

        Transaction suspended = transactionManager.suspend();
        assertThat(suspended)
                .as("the active tx must be suspendable so another thread can complete it")
                .isNotNull();

        AtomicReference<Throwable> completionFailure = new AtomicReference<>();
        Thread completionThread = new Thread(() -> {
            try {
                transactionManager.resume(suspended);
                transactionManager.commit();
            } catch (Throwable failure) {
                completionFailure.set(failure);
            }
        }, COMPLETION_THREAD_NAME);
        completionThread.start();
        completionThread.join(COMPLETION_TIMEOUT_MILLIS);

        assertThat(completionThread.isAlive())
                .as("the foreign thread must finish committing within the timeout")
                .isFalse();
        assertThat(completionFailure.get())
                .as("resuming and committing the suspended tx on another thread must succeed")
                .isNull();

        assertThat(recorder.beforeCompletionCount())
                .as("TransactionBeforeCompletion must still fire exactly once")
                .isEqualTo(1);
        assertThat(recorder.committedCount())
                .as("TransactionCommitted must still fire exactly once")
                .isEqualTo(1);
        assertThat(recorder.rolledBackCount())
                .as("a successful commit must not fire TransactionRolledBack")
                .isZero();
        assertThat(recorder.startedCount())
                .as("TransactionStarted must not fire a second time")
                .isEqualTo(1);

        // The point of the scenario: the completion callbacks ran on the
        // foreign thread, so the events were fired from a thread that
        // never resolved the CDI container itself.
        assertThat(recorder.beforeCompletionThreadName())
                .as("TransactionBeforeCompletion must be observed on the completing thread")
                .isEqualTo(COMPLETION_THREAD_NAME);
        assertThat(recorder.committedThreadName())
                .as("TransactionCommitted must be observed on the completing thread")
                .isEqualTo(COMPLETION_THREAD_NAME);
        assertThat(recorder.committedThreadName())
                .as("the completion thread must differ from the registering thread — "
                        + "otherwise the scenario degenerates into the same-thread case")
                .isNotEqualTo(registrationThreadName);
    }
}
