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
package org.os890.jawelte.tests.wiremock.scenario09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Scenario 09 — two {@code @WireMockEndpoint}-stamped qualifiers
 * <em>plus</em> an unqualified {@code @Inject WireMockServer} in
 * the same subject. Every synthetic bean registered by the CDI
 * extension carries {@code @Default} alongside the user qualifier;
 * with multiple {@code @Default} candidates and an unqualified
 * injection point, CDI raises {@code AmbiguousResolutionException}
 * at deployment time.
 *
 * <p>{@code EngineTestKit} runs {@link Scenario09Subject} so the
 * deployment failure surfaces as a JUnit container failure (the
 * bootstrap path runs in {@code beforeAll}, which is a container
 * lifecycle event). The test asserts that at least one
 * container-level failure was recorded and that the test method
 * itself never ran.
 */
class Scenario09Test {

    @Test
    void unqualifiedInjectionWithMultipleEndpointsFailsDeployment() {
        var execution = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario09Subject.class))
                .execute();

        long anyFailures = execution.allEvents().failed().count();
        long testsSucceeded = execution.testEvents().succeeded().count();

        assertThat(anyFailures)
                .as("deployment-time ambiguity surfaces as a JUnit failure event")
                .isGreaterThan(0);
        assertThat(testsSucceeded)
                .as("the @Test method never completes successfully because deployment fails first")
                .isEqualTo(0);
    }
}
