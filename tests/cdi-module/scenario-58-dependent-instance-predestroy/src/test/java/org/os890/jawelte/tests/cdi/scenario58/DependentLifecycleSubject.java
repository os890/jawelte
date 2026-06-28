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

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Subject driven by {@link Scenario58Test} through {@code EngineTestKit}
 * — not run directly by surefire. The test class is itself a
 * {@code @Dependent} synthetic bean; both it and its injected
 * {@code @Dependent} {@link RecordingCollaborator} must have their
 * {@code @PreDestroy} fired when the framework releases the instance
 * after the test method.
 */
@EnableTestBeans
public class DependentLifecycleSubject {

    @Inject
    private RecordingCollaborator collaborator;

    public DependentLifecycleSubject() {
    }

    @Test
    void usesTheInjectedCollaborator() {
        assertThat(collaborator.greet()).isEqualTo("hi");
    }

    @PreDestroy
    void onDestroy() {
        RecordedDestroys.ENTRIES.add("subject.preDestroy");
    }
}
