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
package org.os890.jawelte.tests.scope.scenario08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.scope.impl.adapter.context.ScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.ScopedBeanInstance;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopeStore;

/**
 * Scenario 08 — direct unit test of {@code ScopeStore.destroyAll}'s
 * exception-aggregation contract.
 *
 * <p>The integration-test path (two {@code @TestMethodScoped} beans
 * with {@code @PreDestroy} that both throw) cannot drive this code
 * end-to-end on OpenWebBeans: OWB's {@code InjectionTargetImpl.preDestroy}
 * logs and swallows {@code @PreDestroy} exceptions instead of
 * propagating them, so {@code Contextual.destroy(...)} returns
 * normally and scope-module's aggregation logic never sees the throws.
 *
 * <p>Testing the aggregation directly bypasses that runtime-specific
 * behaviour and locks in the contract scope-module is responsible for:
 * given multiple {@code Contextual.destroy} calls that throw, the
 * first becomes the primary, the rest are attached via
 * {@code Throwable#addSuppressed}, and the store's map reference is
 * nulled unconditionally in a {@code finally}.
 */
class Scenario08Test {

    @Test
    void destroyAllAggregatesAllThrowsAndNullsTheStore() {
        TestMethodScopeStore store = new TestMethodScopeStore();
        store.allocate();
        Map<Contextual<?>, ScopedBeanInstance<?>> beans = store.map();
        beans.put(throwingContextual("AAA"), new ScopedBeanInstance<>(new Object(), null));
        beans.put(throwingContextual("BBB"), new ScopedBeanInstance<>(new Object(), null));

        Throwable thrown = catchThrowable(store);

        // Iteration order through ConcurrentHashMap is not specified;
        // the contract is that both messages appear (one as primary,
        // the other as suppressed) and exactly one suppressed
        // accompanies the primary.
        Set<String> messages = new LinkedHashSet<>();
        messages.add(thrown.getMessage());
        for (Throwable suppressed : thrown.getSuppressed()) {
            messages.add(suppressed.getMessage());
        }
        assertThat(thrown.getSuppressed())
                .as("exactly one suppressed throwable accompanying the primary")
                .hasSize(1);
        assertThat(messages)
                .as("primary + suppressed cover both Contextual.destroy failures")
                .containsExactlyInAnyOrder("AAA destroy failure", "BBB destroy failure");
        assertThat(thrown).isInstanceOf(IllegalStateException.class);

        // The map reference is nulled in a finally inside destroyAll,
        // even when every entry threw.
        assertThat(store.isAllocated())
                .as("store map must be nulled after destroyAll, even when every destroy threw")
                .isFalse();
    }

    @Test
    void destroyAllOnEmptyStoreIsANoOp() {
        TestMethodScopeStore store = new TestMethodScopeStore();
        // No allocate() call - underlying map stays null.
        assertThat(store.isAllocated()).isFalse();
        store.destroyAll();
        // No exception, store remains unallocated.
        assertThat(store.isAllocated()).isFalse();
    }

    private static Throwable catchThrowable(ScopeStore store) {
        return assertThatThrownBy(store::destroyAll).actual();
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
