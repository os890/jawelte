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
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.transaction.xa.XAResource;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * White-box test for {@link JtaTransactionStrategy}'s nested-commit resume
 * handling. Uses a stub {@link TransactionManager} that models real
 * suspend/resume semantics (resuming while a tx is still associated throws)
 * so the failure path — inner {@code commit()} throws and the suspended
 * outer can't be resumed — can be driven deterministically. The inner tx's
 * failure must remain the thrown exception; a resume failure must not mask
 * it.
 */
public class JtaTransactionStrategyResumeTest {

    /** Reset the JVM-wide strategy statics + the per-thread suspend stack. */
    @BeforeEach
    public void resetStrategyState() throws Exception {
        setStaticField("transactionManager", null);
        suspendedStack().clear();
    }

    /** A resume failure rides along as suppressed; the commit failure stays primary. */
    @Test
    public void resumeFailureDoesNotMaskCommitFailure() throws Exception {
        StubTransactionManager transactionManager = new StubTransactionManager();
        transactionManager.status = Status.STATUS_ACTIVE;
        transactionManager.current = new StubTransaction();
        transactionManager.commitFailure = new SystemException("commit boom");
        setStaticField("transactionManager", transactionManager);
        // A nested @Transactional had suspended an outer tx.
        suspendedStack().push(new StubTransaction());
        JtaTransactionStrategy strategy = new JtaTransactionStrategy();

        Throwable thrown = catchThrowable(strategy::commit);

        assertThat(thrown)
                .as("the inner commit failure must be the thrown exception, not the resume failure")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JTA commit failure");
        assertThat(thrown.getCause())
                .as("the original TM.commit() SystemException must be preserved as the cause")
                .isInstanceOf(SystemException.class);
        assertThat(thrown.getSuppressed())
                .as("the resume failure must be attached as a suppressed exception")
                .hasSize(1);
        assertThat(thrown.getSuppressed()[0])
                .hasMessageContaining("still associated");
        assertThat(transactionManager.resumeCalls)
                .as("resume must not be attempted while the inner tx is still associated")
                .isZero();
    }

    /** After a clean inner commit, the suspended outer is resumed normally. */
    @Test
    public void successfulCommitResumesSuspendedOuter() throws Exception {
        StubTransactionManager transactionManager = new StubTransactionManager();
        transactionManager.status = Status.STATUS_ACTIVE;
        transactionManager.current = new StubTransaction();
        setStaticField("transactionManager", transactionManager);
        StubTransaction outer = new StubTransaction();
        suspendedStack().push(outer);
        JtaTransactionStrategy strategy = new JtaTransactionStrategy();

        Throwable thrown = catchThrowable(strategy::commit);

        assertThat(thrown).as("a clean nested commit must not throw").isNull();
        assertThat(transactionManager.lastResumed)
                .as("the suspended outer transaction must be resumed")
                .isSameAs(outer);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = JtaTransactionStrategy.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    @SuppressWarnings("unchecked")
    private static Deque<Transaction> suspendedStack() throws Exception {
        Field field = JtaTransactionStrategy.class.getDeclaredField("SUSPENDED");
        field.setAccessible(true);
        ThreadLocal<Deque<Transaction>> threadLocal = (ThreadLocal<Deque<Transaction>>) field.get(null);
        Deque<Transaction> stack = threadLocal.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
        }
        return stack;
    }

    /**
     * Stub {@link TransactionManager} modelling suspend/resume: {@code resume}
     * throws when a transaction is still associated with the thread, mirroring
     * a real TM. Only the methods {@link JtaTransactionStrategy#commit()} calls
     * are meaningful.
     */
    private static class StubTransactionManager implements TransactionManager {

        private int status = Status.STATUS_NO_TRANSACTION;

        private Transaction current;

        private SystemException commitFailure;

        private int resumeCalls;

        private Transaction lastResumed;

        @Override
        public void begin() {
            status = Status.STATUS_ACTIVE;
        }

        @Override
        public void commit() throws SystemException {
            if (commitFailure != null) {
                // Leave the tx associated (status ACTIVE) — the failure left
                // the thread/tx association in an indeterminate state.
                throw commitFailure;
            }
            status = Status.STATUS_NO_TRANSACTION;
            current = null;
        }

        @Override
        public void rollback() {
            status = Status.STATUS_NO_TRANSACTION;
            current = null;
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
            resumeCalls++;
            if (status != Status.STATUS_NO_TRANSACTION) {
                throw new IllegalStateException("a transaction is already associated with this thread");
            }
            current = transaction;
            status = Status.STATUS_ACTIVE;
            lastResumed = transaction;
        }
    }

    /** Minimal {@link Transaction} stub with identity equals/hashCode. */
    private static class StubTransaction implements Transaction {

        @Override
        public void registerSynchronization(Synchronization synchronization) {
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
