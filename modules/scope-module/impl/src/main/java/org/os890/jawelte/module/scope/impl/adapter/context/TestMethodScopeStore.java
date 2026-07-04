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
package org.os890.jawelte.module.scope.impl.adapter.context;

/**
 * Bean store for {@code @TestMethodScoped} beans. Holds the live
 * bean map for one test method's lifetime; allocated by
 * {@code ScopeLifecycleAdapter.beforeEach} and destroyed
 * unconditionally in {@code afterEach}. Bound on
 * {@link org.os890.jawelte.core.api.port.TestContext} under its own
 * class as the metadata key.
 *
 * <p>The constructor intentionally leaves the underlying map
 * {@code null}; the lifecycle adapter allocates it via
 * {@link #allocate()} in {@code beforeEach} (unless the
 * {@code BeforeScopeStarted} event is vetoed). The context is active
 * only while the map is allocated, so a {@code @TestMethodScoped} bean
 * is reachable only from within a test method: dereferencing one
 * outside a method — e.g. from {@code @BeforeAll}, before any
 * {@code beforeEach} has run — throws {@code ContextNotActiveException}.
 * Use {@code @TestClassScoped} for fixtures that must live across a
 * test class's methods.
 */
public class TestMethodScopeStore extends ScopeStore {

    /**
     * Construct a store with no live map. Allocation is driven by the
     * lifecycle adapter's {@code allocate()} in {@code beforeEach}.
     */
    public TestMethodScopeStore() {
    }
}
