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
package org.os890.jawelte.tests.jta.scenario49;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Nested {@code @Transactional} under JTA across two persistence
 * units: the outer transaction writes a {@link MarkerA} into PU "a",
 * then calls a nested {@code @Transactional} method that writes a
 * {@link MarkerB} into PU "b". Verifies that both writes commit and
 * are visible to subsequent reads.
 */
@EnableTestBeans
public class Scenario49Test {

    @Inject
    private OuterPuAWriter outerPuAWriter;

    @Inject
    private InnerPuBWriter innerPuBWriter;

    /** No-arg constructor for CDI. */
    public Scenario49Test() {
    }

    @Test
    public void nestedTransactionalWritesPersistInBothPus() {
        outerPuAWriter.persistAcrossBothPus();

        assertThat(outerPuAWriter.countMarkerA())
                .as("outer @Transactional must persist exactly one MarkerA into PU 'a'")
                .isEqualTo(1L);
        assertThat(innerPuBWriter.countMarkerB())
                .as("inner @Transactional must persist exactly one MarkerB into PU 'b'")
                .isEqualTo(1L);
    }
}
