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
package org.os890.jawelte.tests.jpa.scenario17;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * A {@code @ReadOnly} method declared without {@code @Transactional} sees the
 * {@code ReadOnlyInterceptor} fire, observe that no tx is active, and proceed
 * as a documented no-op. The body's return value reaches the caller unchanged.
 */
@EnableTestBeans
public class Scenario17Test {

    @Inject
    private ReadOnlyOnlyService readOnlyOnlyService;

    /** No-arg constructor for CDI. */
    public Scenario17Test() {
    }

    /** @ReadOnly without @Transactional → body runs, value returned unchanged. */
    @Test
    public void readOnlyWithoutTransactionalIsDocumentedNoOp() {
        assertThat(readOnlyOnlyService.computeWithoutTx("hello"))
                .as("ReadOnlyInterceptor must proceed unchanged when no tx is active — "
                        + "the body's return value reaches the caller")
                .isEqualTo("readonly:hello");
    }
}
