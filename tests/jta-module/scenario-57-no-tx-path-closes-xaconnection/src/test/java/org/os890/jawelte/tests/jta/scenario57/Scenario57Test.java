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
package org.os890.jawelte.tests.jta.scenario57;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.jta.impl.adapter.xa.XaDataSourceWrapper;

/**
 * {@code XaDataSourceWrapper}'s defensive no-transaction branch must not
 * leak the physical {@code XAConnection}.
 *
 * <p>When no JTA transaction is active on the calling thread,
 * {@code managedConnection} opens an {@code XAConnection} and returns only
 * its logical {@code Connection} handle. Per the JDBC
 * {@code PooledConnection} contract, closing that handle does NOT close the
 * underlying physical {@code XAConnection} — and on the no-tx path there is
 * no {@code Synchronization} to close it later. The wrapper therefore has to
 * close the physical {@code XAConnection} itself when the caller closes the
 * handle. This test exercises that path with a counting {@code XADataSource}
 * and asserts the open/close balance.
 *
 * <p>No JTA transaction is begun here, so
 * {@code XaDataSourceWrapper.currentTransactionOrNull()} resolves to
 * {@code null} and the defensive branch is taken deterministically. The test
 * needs no CDI container — it drives the wrapper directly.
 */
public class Scenario57Test {

    /** No-arg constructor. */
    public Scenario57Test() {
    }

    @Test
    public void noTxPathClosesUnderlyingXaConnectionWhenHandleCloses() throws Exception {
        CountingXaDataSource countingXaDataSource = new CountingXaDataSource();
        countingXaDataSource.setURL("jdbc:h2:mem:scenario57;DB_CLOSE_DELAY=-1");
        countingXaDataSource.setUser("sa");
        countingXaDataSource.setPassword("");

        XaDataSourceWrapper wrapper = new XaDataSourceWrapper(countingXaDataSource, "scenario57-pu");

        // No JTA tx active -> defensive no-tx branch opens exactly one
        // physical XAConnection and hands back its logical handle.
        Connection handle = wrapper.getConnection();
        assertThat(countingXaDataSource.openedCount())
                .as("the no-tx branch must open exactly one physical XAConnection")
                .isEqualTo(1);
        assertThat(countingXaDataSource.physicalClosedCount())
                .as("the physical XAConnection must still be open before the handle is closed")
                .isZero();

        // Closing the logical handle must release the physical XAConnection.
        // Without the fix the wrapper drops the XAConnection reference and
        // never closes it, so this count would stay 0.
        handle.close();
        assertThat(countingXaDataSource.physicalClosedCount())
                .as("closing the no-tx handle must close the underlying physical XAConnection")
                .isEqualTo(1);
    }
}
