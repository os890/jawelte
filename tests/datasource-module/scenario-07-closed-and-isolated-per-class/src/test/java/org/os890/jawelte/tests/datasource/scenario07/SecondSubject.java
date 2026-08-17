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
package org.os890.jawelte.tests.datasource.scenario07;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Subject class driven by {@link Scenario07Test} through
 * {@code EngineTestKit}. Not a test in its own right: it exists so
 * that a full jawelte lifecycle — container boot, data-source
 * construction, shutdown — can run to completion and be asserted on
 * afterwards, which a test cannot do about itself.
 */
@EnableTestBeans
@DataSourceDefinition(
        name = "java:comp/env/jdbc/SecondDS",
        className = "org.os890.jawelte.tests.datasource.scenario07.RecordingDataSource",
        url = "jdbc:recording:second")
class SecondSubject {

    @Inject
    DataSource dataSource;

    @Test
    void theDeclaredDataSourceIsInjected() {
        assertThat(dataSource).isInstanceOf(RecordingDataSource.class);
        assertThat(((RecordingDataSource) dataSource).url()).isEqualTo("jdbc:recording:second");
    }
}
