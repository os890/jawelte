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
package org.os890.jawelte.tests.testcontrol.scenario32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.scope.api.TestMethodScoped;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * {@code @TestControl(startScopes=…)} must actually suppress a
 * scope-module scope it omits — scope-module honors the
 * {@code BeforeScopeStarted} veto raised by {@code TestControlScopeObserver}.
 *
 * <p>Method 1 lists only {@code @TestClassScoped}, so {@code @TestMethodScoped}
 * is vetoed: accessing a {@code @TestMethodScoped} bean must throw
 * {@code ContextNotActiveException}. Method 2 has no restriction, so the same
 * bean resolves normally (also confirming the allow-list is reset between
 * methods).
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Scenario32Test {

    @Inject
    MethodScopedProbe methodScopedProbe;

    @Test
    @Order(1)
    @TestControl(startScopes = {TestClassScoped.class})
    void vetoedTestMethodScopedThrowsContextNotActive() {
        // @TestMethodScoped is not in the allow-list, so scope-module skips its
        // activation; accessing the bean must throw ContextNotActiveException.
        assertThatThrownBy(methodScopedProbe::ping)
                .isInstanceOf(ContextNotActiveException.class);
    }

    @Test
    @Order(2)
    void unrestrictedMethodResolvesTestMethodScopedNormally() {
        assertThat(methodScopedProbe.ping()).isEqualTo("pong");
    }

    @TestMethodScoped
    public static class MethodScopedProbe {

        public String ping() {
            return "pong";
        }
    }
}
