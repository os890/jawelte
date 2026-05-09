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

import jakarta.annotation.Priority;

import org.os890.jawelte.module.jpa.impl.adapter.cleanup.NativeSqlDeleteDbCleanupStrategy;

/**
 * Test-only wrapper that forces the
 * {@link NativeSqlDeleteDbCleanupStrategy} to win the
 * {@code TestContext.loadService} priority sort over jpa-module's
 * default {@code JdbcTruncateDbCleanupStrategy} (which would otherwise
 * handle circular FKs via H2's {@code SET REFERENTIAL_INTEGRITY} and
 * mask the native-delete fix this scenario verifies).
 *
 * <p>{@code @Priority(50)} — lower number wins the sort. Inherits all
 * behaviour from {@link NativeSqlDeleteDbCleanupStrategy}; the only
 * purpose is to outrank {@code JdbcTruncate} (@Priority MAX_VALUE - 1).
 */
@Priority(50)
public class TestScenarioForcedNativeSqlDeleteStrategy extends NativeSqlDeleteDbCleanupStrategy {

    /** No-arg constructor required by ServiceLoader. */
    public TestScenarioForcedNativeSqlDeleteStrategy() {
    }
}
