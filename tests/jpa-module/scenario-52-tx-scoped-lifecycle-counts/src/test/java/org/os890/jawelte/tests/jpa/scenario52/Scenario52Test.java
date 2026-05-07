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
package org.os890.jawelte.tests.jpa.scenario52;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Two consecutive {@code @Transactional} calls each open and close
 * their own transaction; the {@code @TransactionScoped}
 * {@link TxScopedAuditTracker} therefore goes through two complete
 * lifecycles — two {@code @PostConstruct} invocations and two
 * {@code @PreDestroy} invocations.
 */
@EnableTestBeans
public class Scenario52Test {

    @Inject
    private TxScopedAuditService auditService;

    /** No-arg constructor for CDI. */
    public Scenario52Test() {
    }

    /** Each tx invocation produces exactly one PostConstruct + PreDestroy pair. */
    @Test
    public void twoTransactionalCallsYieldTwoLifecycles() {
        TxScopedAuditTracker.reset();

        auditService.invokeOnceWithinTx();
        auditService.invokeOnceWithinTx();

        assertThat(TxScopedAuditTracker.POST_CONSTRUCT_COUNT)
                .as("one @PostConstruct per @Transactional invocation")
                .hasValue(2);
        assertThat(TxScopedAuditTracker.PRE_DESTROY_COUNT)
                .as("one @PreDestroy per @Transactional invocation")
                .hasValue(2);
    }
}
