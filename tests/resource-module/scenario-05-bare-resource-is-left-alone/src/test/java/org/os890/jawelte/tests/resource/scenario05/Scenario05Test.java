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
package org.os890.jawelte.tests.resource.scenario05;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The documented boundary, asserted rather than assumed.
 *
 * <p>A bare {@code @Resource} has its name inferred in Jakarta EE from
 * the declaring class and field, which is a much larger surface than
 * this module covers today. The behaviour that matters is what it does
 * <em>not</em> do: it must not fail the deployment over a declaration
 * nobody asked it to handle, and it must not stop the named
 * declarations on the same bean from working.
 *
 * <p>If bare support is added later this scenario is the one that
 * changes, and the change will be visible rather than silent.
 */
@EnableTestBeans
class Scenario05Test {

    @Inject
    PartiallyWiredBean bean;

    @Test
    void theBareDeclarationIsLeftAsItWas() {
        assertThat(bean.inferred())
                .as("out of scope means untouched, not guessed at")
                .isNull();
    }

    @Test
    void theNamedDeclarationOnTheSameBeanStillWorks() {
        assertThat(bean.declared())
                .as("one unsupported declaration must not disable the supported ones beside it")
                .isNotNull();
    }
}
