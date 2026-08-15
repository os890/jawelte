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
package org.os890.jawelte.tests.datasource.scenario12;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The narrowed shape from #124: the declaration is carried by one bean,
 * a single plain {@code @Inject DataSource} lives in another, and the
 * test class declares neither. If cdi-module's auto-mock registers a
 * competing {@code @Default} bean, this fails at deployment with
 * {@code AmbiguousResolutionException} rather than in an assertion.
 */
@EnableTestBeans
class Scenario12Test {

    @Inject
    InjectingBean injectingBean;

    @Test
    void aPlainInjectionPointResolvesToTheDeclaredDataSource() throws SQLException {
        assertThat(injectingBean.connectedUrl())
                .as("the injection point must resolve to the declared data source, "
                        + "not compete with an auto-mock of the same type")
                .contains("scenario12");
    }
}
