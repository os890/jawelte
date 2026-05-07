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
package org.os890.jawelte.tests.scope.scenario22;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.os890.jawelte.module.scope.impl.adapter.lifecycle.ScopeLifecycleAdapter;

class Scenario22Test {

    @Test
    void scopeLifecycleAdapterDeclaresZeroInstanceFields() {
        // The adapter is cached by the ServiceLoader and reused across
        // every test class running in the JVM. Any instance field
        // would leak per-test-class state across classes - and worse,
        // race under parallel test-class execution. Guard reflectively.
        assertThat(ScopeLifecycleAdapter.class.getDeclaredFields())
                .as("ScopeLifecycleAdapter must hold no per-test-class instance fields")
                .isEmpty();
    }

    @Test
    void runningTwoTestClassesBackToBackKeepsContextsIsolated() {
        Scenario22FirstSubject.OBSERVED_BEAN_IDENTITIES.clear();
        Scenario22SecondSubject.OBSERVED_BEAN_IDENTITIES.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario22FirstSubject.class))
                .execute();
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario22SecondSubject.class))
                .execute();

        // Each test class booted its own SeContainer, so each got its
        // own pair of context+store instances. The bean instances
        // captured during one class are NOT reachable from the other.
        assertThat(Scenario22FirstSubject.OBSERVED_BEAN_IDENTITIES).hasSize(1);
        assertThat(Scenario22SecondSubject.OBSERVED_BEAN_IDENTITIES).hasSize(1);
        assertThat(Scenario22FirstSubject.OBSERVED_BEAN_IDENTITIES)
                .doesNotContainAnyElementsOf(Scenario22SecondSubject.OBSERVED_BEAN_IDENTITIES);
    }
}
