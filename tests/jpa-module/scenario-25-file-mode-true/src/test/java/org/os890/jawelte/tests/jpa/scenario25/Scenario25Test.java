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
package org.os890.jawelte.tests.jpa.scenario25;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Drives {@link Scenario25FileModeSubject} via the JUnit Platform
 * Test Kit and asserts the {@code @PersistenceConfig(fileMode = true)}
 * skip-after-first behaviour: the first {@code @Test} method runs
 * (and writes into the H2 file), every subsequent method is
 * aborted by {@code JpaLifecycleAdapter.beforeEach}'s
 * {@code TestAbortedException}.
 */
public class Scenario25Test {

    /** No-arg constructor required by JUnit. */
    public Scenario25Test() {
    }

    /** Subject runs first method; second method is aborted by JpaLifecycleAdapter. */
    @Test
    public void fileModeRunsFirstMethodAndAbortsSubsequent() {
        Scenario25FileModeSubject.EXECUTED_METHODS.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario25FileModeSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(2).succeeded(1).aborted(1).failed(0));

        assertThat(Scenario25FileModeSubject.EXECUTED_METHODS)
                .as("only the first method's body ran; the second was aborted in beforeEach")
                .containsExactly("firstMethodPersists");
    }
}
