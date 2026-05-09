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
package org.os890.jawelte.tests.jpa.scenario23;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

import org.junit.jupiter.api.Test;

/**
 * With two persistence units in {@code persistence.xml}, jpa-module's
 * {@code JpaCdiExtension} registers each EMF / EM as {@code @Named(puName)}
 * only — there is no {@code @Default} bean. A CDI bean with an unqualified
 * {@code @Inject EntityManager} therefore has no satisfying bean, and the
 * container raises a deployment exception when it validates injection points.
 *
 * <p>The test boots a fresh {@code SeContainer} programmatically (no
 * {@code @EnableTestBeans} on this test class — that would boot before the
 * assertion line is reached) and asserts that {@code initialize()} throws,
 * with the failure message naming the unsatisfied {@code EntityManager}
 * injection point.
 */
public class Scenario23Test {

    /** No-arg constructor required by JUnit. */
    public Scenario23Test() {
    }

    /** Booting the container with the unqualified consumer on the classpath must throw. */
    @Test
    public void multiPuUnqualifiedInjectionFailsDeployment() {
        assertThatThrownBy(() -> {
            try (SeContainer ignored = SeContainerInitializer.newInstance()
                    .addBeanClasses(UnqualifiedConsumer.class)
                    .initialize()) {
                // unreachable when validation throws as expected
            }
        })
                .as("multi-PU + unqualified @Inject EntityManager must fail container deployment "
                        + "(jpa-module registers @Named-only synthetic beans, no @Default)")
                .satisfies(thrown -> {
                    String message = thrown.getMessage() == null ? "" : thrown.getMessage();
                    assertThat(message)
                            .as("the deployment failure must mention EntityManager AND an "
                                    + "UNSATISFIED-resolution keyword (Weld: 'Unsatisfied'; "
                                    + "OWB: 'not found') — locks the test to the specific "
                                    + "failure mode where jpa-module's @Named-only registration "
                                    + "leaves the unqualified injection point with no satisfying "
                                    + "bean. A different failure mode (e.g. ambiguous resolution "
                                    + "if jpa-module accidentally registered @Default for both "
                                    + "PUs) would NOT mention these keywords and the assertion "
                                    + "would catch the regression — closing punch-list §8.4 / §9.3.")
                            .contains("EntityManager")
                            .containsAnyOf("Unsatisfied", "not found");
                });
    }
}
