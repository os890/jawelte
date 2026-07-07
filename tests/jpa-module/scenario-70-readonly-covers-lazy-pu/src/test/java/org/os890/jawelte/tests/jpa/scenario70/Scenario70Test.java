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
package org.os890.jawelte.tests.jpa.scenario70;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.FlushModeType;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * @ReadOnly must cover EntityManagers lazily created inside the annotated
 * method, and must not leak read-only mode into an enclosing writable tx.
 */
@EnableTestBeans
public class Scenario70Test {

    @Inject
    private ReadOnlyLazyService readOnlyLazyService;

    @Inject
    private OuterWriter outerWriter;

    /** No-arg constructor for CDI. */
    public Scenario70Test() {
    }

    @Test
    public void lazilyJoinedPuIsReadOnlyInsideReadOnlyMethod() {
        assertThat(readOnlyLazyService.lazyPuFlushMode())
                .as("a lazily-joined PU's EM must be COMMIT (auto-flush suppressed) inside @ReadOnly")
                .isEqualTo(FlushModeType.COMMIT);
    }

    @Test
    public void nestedReadOnlyLeavesEnclosingWritable() {
        FlushModeType[] modes = outerWriter.nestedThenOuterFlushModes();
        assertThat(modes[0])
                .as("nested REQUIRES_NEW @ReadOnly EM must be COMMIT")
                .isEqualTo(FlushModeType.COMMIT);
        assertThat(modes[1])
                .as("enclosing writable tx EM must stay AUTO (not made read-only)")
                .isEqualTo(FlushModeType.AUTO);
    }
}
