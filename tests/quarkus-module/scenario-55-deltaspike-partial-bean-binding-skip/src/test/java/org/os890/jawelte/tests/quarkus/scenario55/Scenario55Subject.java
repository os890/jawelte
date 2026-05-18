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
package org.os890.jawelte.tests.quarkus.scenario55;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans
class Scenario55Subject {

    @Inject
    PartialService partialService;

    @Test
    void containerStartShouldFailBecauseAutoMockWasSkipped() {
        // Reaching this point would mean jawelte registered an
        // auto-mock for PartialService, which would mean the
        // hasSyntheticBeanBinding skip is broken. The driver test
        // (Scenario55Test) asserts that the container start fails
        // BEFORE this body runs.
    }
}
