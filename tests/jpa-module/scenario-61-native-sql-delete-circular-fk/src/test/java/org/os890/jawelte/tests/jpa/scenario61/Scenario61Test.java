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
package org.os890.jawelte.tests.jpa.scenario61;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;

/**
 * {@link org.os890.jawelte.module.jpa.impl.adapter.cleanup.NativeSqlDeleteDbCleanupStrategy}
 * handles a two-table FK cycle ({@code Foo.bar_id ↔ Bar.foo_id}) via
 * the two-pass null-update + delete pattern (punch-list §2.3).
 *
 * <p>Why a two-table cycle instead of a single-table self-FK: H2
 * optimises {@code DELETE FROM table} (no WHERE) to defer FK checks
 * until end-of-statement, so the simpler self-FK case in scenario-51
 * happens to work even with naive reverse-order DELETE. A two-table
 * cycle is the canonical shape that breaks reverse-order alone:
 * deleting either table first is blocked by the other table's FK.
 *
 * <p>Forces the native-delete strategy to win the SPI sort over
 * the H2-targeted {@code JdbcTruncateDbCleanupStrategy} (which would
 * otherwise resolve the same case via {@code SET REFERENTIAL_INTEGRITY}
 * and mask the fix).
 *
 * <ul>
 *   <li>Method 1 verifies the SPI sort returns the forced wrapper.</li>
 *   <li>Method 2 persists a Foo + Bar pair with mutual FK references.</li>
 *   <li>Method 3 asserts cleanup wiped both tables — proves Pass 1
 *       (null nullable FKs) successfully broke the cycle so Pass 2
 *       (reverse-order DELETE) could complete. Without the §2.3 fix,
 *       method 2's afterEach throws and method 3 sees stale rows.</li>
 * </ul>
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario61Test {

    @Inject
    private CycleService cycleService;

    /** No-arg constructor for CDI. */
    public Scenario61Test() {
    }

    /** The forced native-delete wrapper wins the priority sort. */
    @Test
    @Order(1)
    public void forcedNativeSqlDeleteWinsPrioritySort() {
        DbCleanupStrategy active = TestContext.loadService(DbCleanupStrategy.class);

        assertThat(active)
                .as("ForcedNativeSqlDeleteStrategy at @Priority(50) must win over JdbcTruncate "
                        + "at @Priority(MAX_VALUE - 1) — otherwise this scenario would silently "
                        + "exercise the truncate path, not the native-delete fix")
                .isInstanceOf(ForcedNativeSqlDeleteStrategy.class);
    }

    /** Persist a Foo/Bar pair with mutual FK references. */
    @Test
    @Order(2)
    public void persistTwoTableCycle() {
        cycleService.persistCycle();
        assertThat(cycleService.countFoo())
                .as("Foo persisted")
                .isEqualTo(1L);
        assertThat(cycleService.countBar())
                .as("Bar persisted, with mutual FK")
                .isEqualTo(1L);
    }

    /**
     * Per-method cleanup (running the forced native-delete strategy)
     * must have wiped both tables. The two-pass approach nulls
     * {@code Foo.bar_id} and {@code Bar.foo_id} in Pass 1, breaking
     * the cycle, then DELETEs both in reverse order. Without the §2.3
     * fix, Pass 2's first DELETE would fail on the FK constraint and
     * the per-method cleanup would error out (rolling back the empty
     * cleanup tx and rethrowing). This method verifies neither row
     * survived.
     */
    @Test
    @Order(3)
    public void cleanupWipedTheTwoTableCycle() {
        assertThat(cycleService.countFoo())
                .as("the two-pass null-update + delete must have wiped Foo. Closes §2.3.")
                .isZero();
        assertThat(cycleService.countBar())
                .as("the two-pass null-update + delete must have wiped Bar. Closes §2.3.")
                .isZero();
    }
}
