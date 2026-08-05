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
package org.os890.jawelte.tests.flowassert.scenario07;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.cdi.uml.dynamic.flow.renderer.runtime.FlowRuntime;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.flowassert.api.RecordedFlows;

/**
 * The recorder is on the classpath of every consumer of this module,
 * because flow-assert-module/impl depends on it. A test class that did
 * not ask for a recording must therefore get none: no interceptor on
 * its beans, no flow, and no cost.
 *
 * <p>This class boots the lifecycle through plain {@code @EnableTestBeans}
 * and leaves {@code @EnableFlowAssert} off.
 */
@EnableTestBeans
class Scenario07Test {

    @Inject
    private OrderService orderService;

    @Test
    void recordsNothingWithoutTheAnnotation() {
        assertThat(orderService.placeOrder("SKU-1", 2)).isEqualTo("SKU-1@5");

        assertThat(RecordedFlows.all()).isEmpty();
    }

    @Test
    void theRecorderIsNotEvenRunning() {
        // the extension marks itself container-managed before it reads the
        // configuration, so a disabled recorder does not arm itself on the
        // first call either: nothing is instrumented at all
        assertThat(FlowRuntime.active()).isNull();
    }
}
