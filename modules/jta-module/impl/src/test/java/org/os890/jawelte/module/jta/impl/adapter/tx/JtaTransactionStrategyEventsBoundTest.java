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
package org.os890.jawelte.module.jta.impl.adapter.tx;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.transaction.xa.XAResource;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * White-box test for {@link JtaTransactionStrategy}'s {@code EVENTS_BOUND}
 * marker lifecycle. Stubs the {@link TransactionManager} / {@link Transaction}
 * directly (no CDI container, no real TM) so a single {@code Transaction}
 * object — standing in for a vendor that reuses / pools instances with
 * identity-based {@code equals} / {@code hashCode} — can be driven across
 * completion and reused. Before the marker was cleared on completion, the
 * second bind of a reused object was silently suppressed (dropping lifecycle
 * events on the sync path).
 */
public class JtaTransactionStrategyEventsBoundTest {

    /** Reset the JVM-wide strategy statics before each test. */
    @BeforeEach
    public void resetStrategyStatics() throws Exception {
        setStaticField("transactionManager", null);
        eventsBoundMap().clear();
    }

    /** A reused Transaction re-binds (registers a new sync) after the prior one completed. */
    @Test
    public void syncPathRebindsAfterCompletionOfReusedTransaction() throws Exception {
        StubTransaction reusedTransaction = new StubTransaction();
        StubTransactionManager transactionManager = new StubTransactionManager();
        transactionManager.setCurrent(reusedTransaction);
        setStaticField("transactionManager", transactionManager);
        JtaTransactionStrategy strategy = new JtaTransactionStrategy();

        strategy.bindLifecycleEventsToCurrentTransaction();
        assertThat(reusedTransaction.registeredSynchronizations())
                .as("first bind registers one lifecycle synchronization")
                .hasSize(1);

        reusedTransaction.fireAfterCompletion(Status.STATUS_COMMITTED);

        // The vendor hands the SAME Transaction object back for a new tx.
        strategy.bindLifecycleEventsToCurrentTransaction();
        assertThat(reusedTransaction.registeredSynchronizations())
                .as("a reused Transaction must re-bind after completion — the marker "
                        + "must be cleared in afterCompletion, not left until GC")
                .hasSize(2);
    }

    /** After a direct-path commit, a reused Transaction still binds on the sync path. */
    @Test
    public void directPathCommitClearsMarkerSoReusedTransactionRebinds() throws Exception {
        StubTransaction reusedTransaction = new StubTransaction();
        StubTransactionManager transactionManager = new StubTransactionManager();
        transactionManager.setBeginTarget(reusedTransaction);
        setStaticField("transactionManager", transactionManager);
        JtaTransactionStrategy strategy = new JtaTransactionStrategy();

        // Direct path: begin() marks the tx, commit() must clear the marker.
        strategy.begin();
        strategy.commit();

        // The SAME Transaction object is reused for a new, vendor-driven tx.
        transactionManager.setCurrent(reusedTransaction);
        strategy.bindLifecycleEventsToCurrentTransaction();
        assertThat(reusedTransaction.registeredSynchronizations())
                .as("after a direct-path commit the marker must be cleared, so a reused "
                        + "Transaction binds (registers a sync) on the sync path")
                .hasSize(1);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = JtaTransactionStrategy.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    @SuppressWarnings("unchecked")
    private static Map<Transaction, Boolean> eventsBoundMap() throws Exception {
        Field field = JtaTransactionStrategy.class.getDeclaredField("EVENTS_BOUND");
        field.setAccessible(true);
        return (Map<Transaction, Boolean>) field.get(null);
    }

    /**
     * Minimal {@link TransactionManager} stub. {@code begin()} makes its
     * configured target the current transaction; {@code commit()} /
     * {@code rollback()} end it. Only the methods the strategy calls are
     * meaningful; the rest throw.
     */
    private static class StubTransactionManager implements TransactionManager {

        private Transaction current;

        private Transaction beginTarget;

        private int status = Status.STATUS_NO_TRANSACTION;

        void setCurrent(Transaction transaction) {
            this.current = transaction;
            this.status = Status.STATUS_ACTIVE;
        }

        void setBeginTarget(Transaction transaction) {
            this.beginTarget = transaction;
        }

        @Override
        public void begin() {
            current = beginTarget;
            status = Status.STATUS_ACTIVE;
        }

        @Override
        public void commit() {
            status = Status.STATUS_NO_TRANSACTION;
        }

        @Override
        public void rollback() {
            status = Status.STATUS_NO_TRANSACTION;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public Transaction getTransaction() {
            return current;
        }

        @Override
        public void setRollbackOnly() {
            status = Status.STATUS_MARKED_ROLLBACK;
        }

        @Override
        public void setTransactionTimeout(int seconds) {
        }

        @Override
        public Transaction suspend() {
            Transaction suspended = current;
            current = null;
            status = Status.STATUS_NO_TRANSACTION;
            return suspended;
        }

        @Override
        public void resume(Transaction transaction) {
            current = transaction;
            status = Status.STATUS_ACTIVE;
        }
    }

    /**
     * Minimal {@link Transaction} stub with identity {@code equals} /
     * {@code hashCode} (the reuse premise). Records registered
     * synchronizations and can replay {@code afterCompletion} to them.
     */
    private static class StubTransaction implements Transaction {

        private final List<Synchronization> synchronizations = new ArrayList<>();

        List<Synchronization> registeredSynchronizations() {
            return synchronizations;
        }

        void fireAfterCompletion(int status) {
            for (Synchronization synchronization : new ArrayList<>(synchronizations)) {
                synchronization.beforeCompletion();
                synchronization.afterCompletion(status);
            }
        }

        @Override
        public void registerSynchronization(Synchronization synchronization) {
            synchronizations.add(synchronization);
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }

        @Override
        public void setRollbackOnly() {
        }

        @Override
        public int getStatus() {
            return Status.STATUS_ACTIVE;
        }

        @Override
        public boolean enlistResource(XAResource xaResource) {
            return true;
        }

        @Override
        public boolean delistResource(XAResource xaResource, int flag) {
            return true;
        }
    }
}
