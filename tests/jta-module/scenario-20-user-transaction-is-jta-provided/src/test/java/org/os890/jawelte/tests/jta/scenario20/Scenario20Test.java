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
package org.os890.jawelte.tests.jta.scenario20;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jpa.impl.adapter.tx.UserTransactionImpl;

/**
 * Ticket-006 scenario #20 — {@code @Inject UserTransaction} resolves
 * to the JTA implementation's standard {@code UserTransaction} (the
 * one supplied by the active {@code TransactionManagerProvider}),
 * not jpa-module's delegating {@link UserTransactionImpl}. The test
 * unwraps any CDI normal-scope proxy via {@code Status.STATUS_NO_TRANSACTION}
 * (every JTA implementation reports that status outside an active
 * tx) — and verifies the underlying type is not the project's helper.
 */
@EnableTestBeans
public class Scenario20Test {

    @Inject
    private UserTransaction userTransaction;

    /** No-arg constructor for CDI. */
    public Scenario20Test() {
    }

    @Test
    public void userTransactionIsTheJtaProvidedOne() throws Exception {
        // Behaviour check via the CDI-injected proxy: the bean's
        // status outside any tx must be STATUS_NO_TRANSACTION (every
        // JTA impl reports that) and the proxy must NOT bottom out
        // in jpa-module's delegating UserTransactionImpl.
        assertThat(userTransaction.getStatus())
                .as("UserTransaction.getStatus() outside any tx must be STATUS_NO_TRANSACTION")
                .isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(userTransaction)
                .as("Injected UserTransaction must NOT be jpa-module's delegating UserTransactionImpl")
                .isNotInstanceOf(UserTransactionImpl.class);

        // Identity check via the strategy: bypass the CDI proxy and
        // verify the underlying type comes from a JTA-implementation
        // package. The strategy.userTransaction() return value is
        // exactly what JpaCdiExtension registers as the synthetic CDI
        // bean's source.
        UserTransaction strategyUserTransaction =
                TestContext.loadService(TransactionStrategy.class).userTransaction();
        String runtimeClassName = strategyUserTransaction.getClass().getName();
        assertThat(runtimeClassName)
                .as("strategy.userTransaction() must yield a JTA-implementation UserTransaction")
                .satisfiesAnyOf(
                        name -> assertThat(name).startsWith("org.apache.geronimo.transaction"),
                        name -> assertThat(name).startsWith("com.arjuna.ats."),
                        name -> assertThat(name).startsWith("com.atomikos."));
    }
}
