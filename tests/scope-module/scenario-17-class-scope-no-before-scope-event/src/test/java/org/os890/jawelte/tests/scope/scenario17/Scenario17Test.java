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
package org.os890.jawelte.tests.scope.scenario17;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

@EnableTestBeans
class Scenario17Test {

    @Inject
    Bean bean;

    @Test
    void firstMethodTouchesClassScopedBean() {
        bean.touch();
    }

    @Test
    void secondMethodTouchesClassScopedBean() {
        bean.touch();
    }

    @AfterAll
    static void assertNoBeforeScopeStartedFiredForTestClassScoped() {
        // ScopeLifecycleAdapter never fires BeforeScopeStarted for
        // TestClassScoped (the class context is active from its
        // store's constructor onward; there is no activation step).
        // The observer in ClassScopeStartObserver never increments.
        assertThat(ClassScopeStartObserver.CLASS_SCOPE_START_COUNT.get())
                .as("BeforeScopeStarted must NOT fire for TestClassScoped at any time")
                .isEqualTo(0);
    }

    @TestClassScoped
    public static class Bean {

        public void touch() {
        }
    }
}
