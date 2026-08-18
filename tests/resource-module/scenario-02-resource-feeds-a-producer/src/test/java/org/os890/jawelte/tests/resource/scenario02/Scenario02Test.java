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
package org.os890.jawelte.tests.resource.scenario02;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The reported failure, from the consuming end.
 *
 * <p>Without {@code @Resource} support this does not fail at
 * deployment - it fails at first use, with
 * {@code IllegalProductException: ... scope type must be @Dependent to
 * create null instance}, which names the producer rather than the
 * unfilled field that caused it. An application hitting that has to
 * keep a test-only producer purely to work around it, which is the
 * scaffolding this module exists to remove.
 */
@EnableTestBeans
class Scenario02Test {

    @Inject
    ReportingService reportingService;

    @Test
    void theProducerProducesBecauseItsResourceFieldWasFilled() throws SQLException {
        assertThat(reportingService.connectedUrl())
                .as("the producer's only input is a @Resource field, so this is that field")
                .contains("scenario02");
    }
}
