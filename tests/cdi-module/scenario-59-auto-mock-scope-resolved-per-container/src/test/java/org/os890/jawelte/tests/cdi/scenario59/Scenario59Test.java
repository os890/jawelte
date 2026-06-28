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
package org.os890.jawelte.tests.cdi.scenario59;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.os890.jawelte.module.cdi.impl.adapter.extension.TestBeansCdiExtension;

/**
 * The auto-mock default scope for non-JDK types must be resolved per
 * container from the active MP Config layer — NOT captured once in a
 * static field and frozen for the JVM ("first container wins").
 *
 * <p>Runs {@link AutoMockScopeSubject} in two containers with a
 * different {@code auto-mock.default-scope} per run (via a system
 * property, which MP Config reads live); each container's auto-mock bean
 * must report its own configured scope. On the static-field
 * implementation the second container reports the first's frozen scope.
 * Uses {@code @RequestScoped} / {@code @ApplicationScoped} (always on the
 * classpath) as two distinct resolvable scopes.
 */
class Scenario59Test {

    @Test
    void autoMockScopeIsResolvedPerContainerNotFrozenForTheJvm() {
        RecordedScopes.ENTRIES.clear();
        String key = TestBeansCdiExtension.AUTO_MOCK_DEFAULT_SCOPE_KEY;
        try {
            System.setProperty(key, RequestScoped.class.getName());
            runSubject();
            System.setProperty(key, ApplicationScoped.class.getName());
            runSubject();

            assertThat(RecordedScopes.ENTRIES)
                    .as("each container's auto-mock scope must reflect its own config, "
                            + "not the scope frozen by the first container")
                    .containsExactly(RequestScoped.class.getName(), ApplicationScoped.class.getName());
        } finally {
            System.clearProperty(key);
        }
    }

    private static void runSubject() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(AutoMockScopeSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }
}
