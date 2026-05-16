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
package org.os890.jawelte.tests.batch.scenario15;

import jakarta.transaction.NotSupportedException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

/**
 * No-op {@link TransactionManager} for scenario 15. The
 * batchlet under test never opens a transaction; this stub
 * satisfies JBeret's {@code AbstractJobOperator.invokeTransaction(...)}
 * call chain without bringing JTA infrastructure into the
 * scenario classpath. All methods are no-ops; {@link #getStatus()}
 * reports {@link Status#STATUS_NO_TRANSACTION} so JBeret's
 * suspend/resume logic stays in the "no active transaction" branch.
 */
public class NoOpTransactionManager implements TransactionManager {

    public NoOpTransactionManager() {
    }

    @Override
    public void begin() throws NotSupportedException, SystemException {
    }

    @Override
    public void commit() {
    }

    @Override
    public int getStatus() throws SystemException {
        return Status.STATUS_NO_TRANSACTION;
    }

    @Override
    public Transaction getTransaction() throws SystemException {
        return null;
    }

    @Override
    public void resume(Transaction tobj) {
    }

    @Override
    public void rollback() {
    }

    @Override
    public void setRollbackOnly() {
    }

    @Override
    public void setTransactionTimeout(int seconds) {
    }

    @Override
    public Transaction suspend() throws SystemException {
        return null;
    }
}
