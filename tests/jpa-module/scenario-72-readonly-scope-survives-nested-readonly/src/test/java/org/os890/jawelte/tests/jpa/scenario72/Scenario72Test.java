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
package org.os890.jawelte.tests.jpa.scenario72;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.FlushModeType;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The read-only scope is depth-tracked: a nested {@code @ReadOnly}
 * returning must not end the enclosing {@code @ReadOnly} scope, so a
 * persistence unit lazily joined <em>after</em> the nested call returns
 * is still read-only.
 */
@EnableTestBeans
public class Scenario72Test {

    @Inject
    private OuterReadOnlyService outerReadOnlyService;

    /** No-arg constructor for CDI. */
    public Scenario72Test() {
    }

    /** A PU joined after a nested @ReadOnly returns is still read-only. */
    @Test
    public void lazyPuJoinedAfterNestedReadOnlyIsStillReadOnly() {
        FlushModeType[] modes = outerReadOnlyService.nestedThenLazyPuFlushModes();

        assertThat(modes[0])
                .as("nested @ReadOnly's own PU-a must be COMMIT")
                .isEqualTo(FlushModeType.COMMIT);
        assertThat(modes[1])
                .as("PU-b, lazily joined after the nested @ReadOnly returned, "
                        + "must still be COMMIT (outer scope survives by depth)")
                .isEqualTo(FlushModeType.COMMIT);
    }
}
