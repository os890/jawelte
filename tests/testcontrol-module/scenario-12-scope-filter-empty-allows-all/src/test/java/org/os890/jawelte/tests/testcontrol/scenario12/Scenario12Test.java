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
package org.os890.jawelte.tests.testcontrol.scenario12;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.scope.api.TestMethodScoped;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Scenario 12 — {@code @TestControl(startScopes = {})}. Empty array
 * is the documented sentinel for "all scope-module scopes activate
 * normally"; the {@code TestControlScopeObserver} must NOT veto any
 * {@code BeforeScopeStarted} event, which means
 * {@code @TestMethodScoped} and {@code @TestClassScoped} beans are
 * both reachable in the test method.
 */
@EnableTestBeans
class Scenario12Test {

    @Inject
    MethodCounter methodCounter;

    @Inject
    ClassMarker classMarker;

    @Test
    @TestControl(startScopes = {})
    void bothScopesActiveWithEmptyStartScopes() {
        assertThat(methodCounter.incrementAndGet()).isEqualTo(1);
        classMarker.setValue("set-from-scenario-12");
        assertThat(classMarker.getValue()).isEqualTo("set-from-scenario-12");
    }

    @TestMethodScoped
    public static class MethodCounter {

        private int value;

        public int incrementAndGet() {
            return ++value;
        }
    }

    @TestClassScoped
    public static class ClassMarker {

        private String value;

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
