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
package org.os890.jawelte.tests.jta.scenario17;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Ticket-006 scenarios #17 + #18 — {@code @TransactionScoped} under
 * JTA. The bean is created on first dereference inside the JTA
 * transaction and destroyed when the transaction completes;
 * {@code @PreDestroy} fires once per transaction, on both commit and
 * rollback paths. The bean-store lifecycle is owned by jpa-module's
 * {@code TransactionScopedContext} (TICKET-005); this scenario proves
 * the same machinery works with the {@code JtaTransactionStrategy}
 * driving the begin / commit / rollback boundaries.
 */
@EnableTestBeans
public class Scenario17Test {

    @Inject
    private PerTxBeanReader reader;

    /** No-arg constructor for CDI. */
    public Scenario17Test() {
    }

    @Test
    public void perTxBeanIsCreatedAndDestroyedPerJtaTransaction() {
        PerTxBean.PRE_DESTROY_COUNT.set(0);

        String firstTxId = reader.readIdInsideJtaTx();
        String secondTxId = reader.readIdInsideJtaTx();

        assertThat(firstTxId).isNotBlank();
        assertThat(secondTxId).isNotBlank();
        assertThat(firstTxId)
                .as("two separate JTA txs must each get their own @TransactionScoped instance")
                .isNotEqualTo(secondTxId);

        assertThat(PerTxBean.PRE_DESTROY_COUNT.get())
                .as("@PreDestroy must fire once per JTA tx that dereferenced the bean")
                .isEqualTo(2);
    }

    @Test
    public void perTxBeanPreDestroyFiresOnRollback() {
        PerTxBean.PRE_DESTROY_COUNT.set(0);

        assertThatThrownBy(reader::readIdAndRollback)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("intentional rollback driver");

        assertThat(PerTxBean.PRE_DESTROY_COUNT.get())
                .as("@PreDestroy must fire on the rollback path, just as on commit")
                .isEqualTo(1);
    }
}
