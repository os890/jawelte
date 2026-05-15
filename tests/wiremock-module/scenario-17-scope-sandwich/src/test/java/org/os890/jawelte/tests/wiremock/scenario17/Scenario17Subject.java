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
package org.os890.jawelte.tests.wiremock.scenario17;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

/**
 * Subject for scenario 17. Touches the
 * {@link Scenario17ScopeObserver @TestClassScoped} bean so
 * it actually gets instantiated; when scope-module deactivates
 * the class scope in its {@code afterAll}, the bean's
 * {@code @PreDestroy} probes the captured
 * {@code WireMockServer} and records the result for
 * {@link Scenario17Test}.
 */
@EnableWireMock
class Scenario17Subject {

    @Inject
    private Scenario17ScopeObserver observer;

    @Test
    void touchTheObserverSoItGetsInstantiated() {
        observer.touch();
    }
}
