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
package org.os890.jawelte.tests.scope.scenario16;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.scope.impl.adapter.context.ScopedBeanInstance;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopedContext;

/**
 * Scenario 16 — direct unit test of {@code TestClassScopeStore} +
 * {@code TestClassScopedContext.deactivate()} exception aggregation.
 *
 * <p>Same rationale as Scenario 08: OWB swallows {@code @PreDestroy}
 * exceptions inside {@code InjectionTargetImpl.preDestroy}, so the
 * end-to-end path cannot exercise scope-module's aggregation logic
 * on the OWB profile. Drives the contract directly through the
 * scope-module SPI.
 */
class Scenario16Test {

    @Test
    void deactivateAggregatesAllThrowsAndNullsTheStore() {
        TestClassScopeStore store = new TestClassScopeStore();
        TestClassScopedContext context = new TestClassScopedContext(store);
        Map<Contextual<?>, ScopedBeanInstance<?>> beans = store.map();
        beans.put(throwingContextual("AAA"), new ScopedBeanInstance<>(new Object(), null));
        beans.put(throwingContextual("BBB"), new ScopedBeanInstance<>(new Object(), null));

        Throwable thrown = assertThatThrownBy(context::deactivate).actual();

        Set<String> messages = new LinkedHashSet<>();
        messages.add(thrown.getMessage());
        for (Throwable suppressed : thrown.getSuppressed()) {
            messages.add(suppressed.getMessage());
        }
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(messages).containsExactlyInAnyOrder("AAA destroy failure", "BBB destroy failure");
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(store.isAllocated()).isFalse();
    }

    private static Contextual<Object> throwingContextual(String tag) {
        return new Contextual<>() {
            @Override
            public Object create(CreationalContext<Object> creationalContext) {
                return new Object();
            }

            @Override
            public void destroy(Object instance, CreationalContext<Object> creationalContext) {
                throw new IllegalStateException(tag + " destroy failure");
            }
        };
    }
}
