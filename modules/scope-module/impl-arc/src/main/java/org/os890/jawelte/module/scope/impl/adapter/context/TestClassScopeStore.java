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
 * Bean store for {@code @TestClassScoped} beans. Mirrors
 * {@code @ApplicationScoped}: the underlying map is allocated
 * eagerly in the constructor (during {@code AfterBeanDiscovery}, by
 * {@code TestScopeCdiExtension}) and stays live for the test class's
 * lifetime, until {@code ScopeLifecycleAdapter.afterAll} calls
 * {@code destroyAll()}. Bound on
 * {@link org.os890.jawelte.core.api.port.TestContext} under its own
 * class as the metadata key.
 */
public class TestClassScopeStore extends ScopeStore {

    /**
     * Construct a store with the live map allocated immediately.
     * From this point on the store reports {@code isAllocated() == true}.
     */
    public TestClassScopeStore() {
        allocate();
    }
}
