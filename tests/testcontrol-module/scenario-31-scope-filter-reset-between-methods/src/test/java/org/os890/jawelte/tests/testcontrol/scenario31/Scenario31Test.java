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
package org.os890.jawelte.tests.testcontrol.scenario31;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * The scope-filter allow-list must be reset after each test method so
 * every method starts with a fresh scope context.
 *
 * <p>{@code TestControlScopeObserver} is {@code @ApplicationScoped}
 * (container lifetime), and {@code CdiTestBeanContainer.beforeEach} fires
 * {@code BeforeScopeStarted(RequestScoped)} <em>before</em> the testcontrol
 * adapter reconfigures the allow-list for the current method. If the adapter
 * did not clear the allow-list in {@code afterEach}, a method that follows a
 * restrictive {@code @TestControl(startScopes=…)} method would evaluate its
 * container-managed request scope against the previous method's stale list
 * and get it wrongly vetoed — so accessing a {@code @RequestScoped} bean
 * would throw {@code ContextNotActiveException}.
 *
 * <p>The two methods are ordered: method 1 sets a restrictive allow-list that
 * excludes {@code @RequestScoped}; method 2 (no {@code @TestControl}) must
 * still resolve a {@code @RequestScoped} bean. Method 1 also shows that
 * {@code @RequestScoped} stays active under a restrictive {@code startScopes}
 * for the declaring method itself.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Scenario31Test {

    @Inject
    RequestScopedProbe requestScopedProbe;

    @Test
    @Order(1)
    @TestControl(startScopes = {TestClassScoped.class})
    void restrictiveStartScopesStillLeavesRequestScopeActive() {
        // Even with a startScopes list that omits @RequestScoped, the
        // container-managed request scope is active for this method.
        assertThat(requestScopedProbe.ping()).isEqualTo("pong");
    }

    @Test
    @Order(2)
    void nextMethodGetsFreshRequestScopeContext() {
        // No @TestControl here. Without the afterEach allow-list reset, this
        // method's request scope would be vetoed by method 1's stale
        // {@code {TestClassScoped}} list and this call would throw
        // ContextNotActiveException.
        assertThat(requestScopedProbe.ping()).isEqualTo("pong");
    }

    @RequestScoped
    public static class RequestScopedProbe {

        public String ping() {
            return "pong";
        }
    }
}
