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
package org.os890.jawelte.tests.scope.scenario11;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestMethodScoped;

@EnableTestBeans
class Scenario11Test {

    @Inject
    MethodScopedBean methodScoped;

    @Inject
    RequestScopedBean requestScoped;

    @Test
    void bothScopesActiveWithIndependentStores() {
        methodScoped.set("method");
        requestScoped.set("request");
        assertThat(methodScoped.get()).isEqualTo("method");
        assertThat(requestScoped.get()).isEqualTo("request");
        // Cross-scope writes don't bleed - the two contexts are
        // independent. CDI normal-scope semantics for both.
    }

    @TestMethodScoped
    public static class MethodScopedBean {

        private String value = "<unset>";

        public void set(String value) {
            this.value = value;
        }

        public String get() {
            return this.value;
        }
    }

    @RequestScoped
    public static class RequestScopedBean {

        private String value = "<unset>";

        public void set(String value) {
            this.value = value;
        }

        public String get() {
            return this.value;
        }
    }
}
