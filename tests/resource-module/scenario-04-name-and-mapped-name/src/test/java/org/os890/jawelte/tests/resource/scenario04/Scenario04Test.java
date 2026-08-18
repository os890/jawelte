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
package org.os890.jawelte.tests.resource.scenario04;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code lookup} is the member the ticket asked for, but production
 * code uses all three. Supporting only one would mean an application
 * normalising its declarations before its tests would run, which is the
 * same kind of test-shaped edit the module exists to avoid.
 *
 * <p>Two databases rather than one, so a member resolving to the wrong
 * entry is caught rather than passing by coincidence.
 */
@EnableTestBeans
class Scenario04Test {

    @Inject
    MixedDeclarationRepository repository;

    @Test
    void nameResolves() throws SQLException {
        assertThat(repository.ordersUrl()).contains("scenario04orders");
    }

    @Test
    void mappedNameResolves() throws SQLException {
        assertThat(repository.auditUrl()).contains("scenario04audit");
    }
}
