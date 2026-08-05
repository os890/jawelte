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
package org.os890.jawelte.tests.flowassert.scenario03;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.flowassert.api.EnableFlowAssert;
import org.os890.jawelte.module.flowassert.api.ExpectedFlow;
import org.os890.jawelte.module.flowassert.api.RecordedFlows;

/**
 * The same beans and the same test as scenario 01, with the expected
 * file written in PlantUML instead of Mermaid. Nothing else differs -
 * no configuration, no annotation attribute, no recorder setting: the
 * extension of the file the convention finds decides the notation, and
 * the recording is rendered in it.
 *
 * <p>Also covers the probing order of the convention: {@code .mmd} and
 * {@code .mermaid} are probed before {@code .puml}, and only the
 * PlantUML file exists.
 */
@EnableFlowAssert
class Scenario03Test {

    @Inject
    private OrderService orderService;

    @Test
    @ExpectedFlow
    void placesOrder() {
        assertThat(orderService.placeOrder("SKU-1", 2)).isEqualTo("SKU-1@5");
    }

    @Test
    void rendersTheRecordingInEitherNotationOnDemand() {
        orderService.placeOrder("SKU-1", 2);

        assertThat(RecordedFlows.combinedDiagram("plantuml")).startsWith("@startuml");
        assertThat(RecordedFlows.combinedDiagram("mermaid")).startsWith("sequenceDiagram");
    }
}
