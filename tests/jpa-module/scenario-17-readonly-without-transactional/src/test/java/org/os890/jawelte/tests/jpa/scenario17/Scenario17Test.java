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
package org.os890.jawelte.tests.jpa.scenario17;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * A {@code @ReadOnly} method declared without {@code @Transactional} is
 * a documented no-op: the body runs and its return value reaches the
 * caller unchanged.
 *
 * <p><strong>Coverage caveat (punch-list §8.1).</strong> The "no-op" path
 * is inherently un-testable through black-box assertions — by definition
 * it leaves no observable state change. A pure pass-through replacement
 * of {@code ReadOnlyInterceptor.aroundInvoke} would also satisfy the
 * assertion below; a missing interceptor binding entirely would too.
 * <strong>This test does NOT prove that {@code ReadOnlyInterceptor}
 * specifically fired</strong> — it only proves that calling a
 * {@code @ReadOnly}-only method returns the body's value, which is the
 * documented contract. Strengthening would require a side-channel
 * (e.g. firing a CDI event from the interceptor for test observation),
 * which would expand prod surface area solely to make this case
 * verifiable. Decision (2026-05-08): keep the test, label it honestly,
 * and document the inherent gap rather than add prod-only-for-tests
 * machinery.
 */
@EnableTestBeans
public class Scenario17Test {

    @Inject
    private ReadOnlyOnlyService readOnlyOnlyService;

    /** No-arg constructor for CDI. */
    public Scenario17Test() {
    }

    /**
     * @ReadOnly without @Transactional → body runs, return value reaches
     * the caller. <strong>Does not verify the interceptor specifically
     * fired</strong> — see the class-level §8.1 caveat.
     */
    @Test
    public void readOnlyWithoutTransactionalReturnsBodyValueUnchanged() {
        assertThat(readOnlyOnlyService.computeWithoutTx("hello"))
                .as("the body's return value must reach the caller — verifies the documented "
                        + "contract that a @ReadOnly-only method is a no-op pass-through. "
                        + "NB: this assertion would also pass against a stripped-to-no-op "
                        + "ReadOnlyInterceptor or even no interceptor at all (§8.1).")
                .isEqualTo("readonly:hello");
    }
}
