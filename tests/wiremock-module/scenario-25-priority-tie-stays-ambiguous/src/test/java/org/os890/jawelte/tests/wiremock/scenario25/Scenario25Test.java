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
package org.os890.jawelte.tests.wiremock.scenario25;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Scenario 25 — when two discovered qualifiers tie at the
 * lowest {@code @Priority} value, the CDI extension declares no
 * implicit {@code @Default} winner. Every synthetic bean keeps
 * {@code @Default} (legacy behaviour) and an unqualified
 * {@code @Inject WireMockServer} surfaces the standard
 * {@code AmbiguousResolutionException} at deployment.
 *
 * <p>Runs {@link Scenario25Subject} via {@code EngineTestKit}
 * and asserts the failure path — same diagnostic shape as
 * scenario 09's no-priority case, this time triggered by a
 * priority tie.
 */
class Scenario25Test {

    @Test
    void tiedPrioritiesStillProduceAmbiguousUnqualifiedInjection() {
        var execution = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario25Subject.class))
                .execute();

        long anyFailures = execution.allEvents().failed().count();
        long testsSucceeded = execution.testEvents().succeeded().count();

        assertThat(anyFailures)
                .as("tied @Priority leaves the synthetic beans ambiguous on unqualified injection")
                .isGreaterThan(0);
        assertThat(testsSucceeded)
                .as("the @Test method never completes successfully because deployment fails first")
                .isEqualTo(0);
    }
}
