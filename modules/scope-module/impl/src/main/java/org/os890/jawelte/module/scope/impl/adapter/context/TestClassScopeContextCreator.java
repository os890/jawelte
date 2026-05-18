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

import java.util.Map;

import io.quarkus.arc.ContextCreator;
import io.quarkus.arc.InjectableContext;

/**
 * ArC {@link ContextCreator} for {@code @TestClassScoped}. Invoked
 * once per {@code Arc.initialize}; reads the per-test-class store
 * from {@link TestScopeCurrentStores#classStore()} (set by
 * {@code ScopeArcContextContributor} during the preceding
 * {@code beforeAll}) and returns a {@link TestClassScopedContext}
 * delegating to it.
 *
 * <p>Has a public no-arg constructor because ArC's
 * {@code ContextConfigurator.creator(Class)} pathway instantiates
 * the creator reflectively.
 */
public class TestClassScopeContextCreator implements ContextCreator {

    /** No-arg constructor required by ArC's reflective creator lookup. */
    public TestClassScopeContextCreator() {
    }

    @Override
    public InjectableContext create(Map<String, Object> params) {
        return new TestClassScopedContext(TestScopeCurrentStores.classStore());
    }
}
