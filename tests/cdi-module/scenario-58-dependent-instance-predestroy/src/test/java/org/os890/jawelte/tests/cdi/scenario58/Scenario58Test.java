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
package org.os890.jawelte.tests.cdi.scenario58;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * The {@code @Dependent} test instance and its injected {@code @Dependent}
 * collaborators must be released after the test method so their
 * {@code @PreDestroy} runs — the {@code @Dependent} lifecycle contract.
 *
 * <p>Drives {@link DependentLifecycleSubject} through {@code EngineTestKit}
 * (so the full per-method lifecycle, including the framework's
 * {@code afterEach}, completes) and then asserts both {@code @PreDestroy}
 * callbacks fired. On the unfixed code the producer's
 * {@code CreationalContext} is abandoned, so neither fires.
 */
class Scenario58Test {

    @Test
    void dependentTestInstanceAndCollaboratorsArePreDestroyed() {
        RecordedDestroys.ENTRIES.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(DependentLifecycleSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        assertThat(RecordedDestroys.ENTRIES)
                .as("the @Dependent test instance and its injected @Dependent collaborator "
                        + "must both be @PreDestroy-ed after the test method")
                .contains("subject.preDestroy", "collaborator.preDestroy");
    }
}
