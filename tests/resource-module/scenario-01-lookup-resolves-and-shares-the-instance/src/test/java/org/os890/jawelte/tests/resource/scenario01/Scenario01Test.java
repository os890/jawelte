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
package org.os890.jawelte.tests.resource.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code @Resource(lookup = ...)} is how a Jakarta EE component obtains
 * a container-managed resource, and it is what {@code @DataSourceDefinition}
 * exists to feed. Without support for it the field is left null and the
 * application has to keep a test-only producer whose entire job is to
 * obtain the same data source a different way.
 *
 * <p>The identity assertion is the one that matters. Resolving to
 * <em>a</em> working data source would not be enough: an application
 * that migrates its schema through one idiom and reads through the
 * other has to be talking to a single connection pool, or the test
 * observes a database the deployed application never uses.
 */
@EnableTestBeans
class Scenario01Test {

    @Inject
    OrderRepository orderRepository;

    @Test
    void theDeclaredNameResolvesToAWorkingDataSource() throws SQLException {
        assertThat(orderRepository.declared())
                .as("a @Resource field must not be left null")
                .isNotNull();
        assertThat(orderRepository.connectedUrl())
                .as("the resolved data source has to be the declared one, not any data source")
                .contains("scenario01");
    }

    @Test
    void bothIdiomsReachTheSameObject() {
        assertThat(orderRepository.declared())
                .as("one declaration is one data source, however it is obtained")
                .isSameAs(orderRepository.injected());
    }
}
