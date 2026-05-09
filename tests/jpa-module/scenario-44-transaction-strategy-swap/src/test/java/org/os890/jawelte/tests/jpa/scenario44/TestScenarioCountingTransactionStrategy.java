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
package org.os890.jawelte.tests.jpa.scenario44;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.jpa.impl.adapter.tx.DefaultResourceLocalTransactionStrategy;

/**
 * Test-only {@code TransactionStrategy} at {@code @Priority(100)} — wins
 * over the addon's {@link DefaultResourceLocalTransactionStrategy}
 * ({@code @Priority(Integer.MAX_VALUE)}). Inherits the default's
 * RESOURCE_LOCAL behaviour so any test that bootstraps a CDI container
 * with this strategy active still works end-to-end. Overrides
 * {@link #begin()} and {@link #commit()} to bump a static counter — the
 * test asserts the counter is non-zero after a real {@code @Transactional}
 * call, proving the framework's interceptor / lifecycle actually
 * delegates to the SPI-resolved strategy, not just that the SPI
 * returns this class.
 */
@Priority(100)
public class TestScenarioCountingTransactionStrategy extends DefaultResourceLocalTransactionStrategy {

    /**
     * Static so the test can read it across the
     * {@code TestContext.loadService} pattern, which may instantiate
     * a fresh strategy on every call. Each {@code begin()} bumps it.
     */
    public static final AtomicInteger BEGIN_COUNT = new AtomicInteger();

    /** Each {@code commit()} bumps it. */
    public static final AtomicInteger COMMIT_COUNT = new AtomicInteger();

    /** No-arg constructor required by ServiceLoader. */
    public TestScenarioCountingTransactionStrategy() {
    }

    @Override
    public void begin() {
        BEGIN_COUNT.incrementAndGet();
        super.begin();
    }

    @Override
    public void commit() {
        COMMIT_COUNT.incrementAndGet();
        super.commit();
    }
}
