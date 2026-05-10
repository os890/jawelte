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
package org.os890.jawelte.tests.jta.scenario44;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The {@code org.os890.jawelte.module.jta.xa-data-source-class} MP
 * Config key swaps the default H2 {@code JdbcDataSource} for a
 * user-supplied {@link CountingXaDataSource}. The test asserts the
 * counters on that class move during a real JTA tx — proving the
 * config-driven class is the one the resolver instantiates and
 * Hibernate's connection-acquire path goes through.
 */
@EnableTestBeans
public class Scenario44Test {

    @Inject
    private MarkerService service;

    /** No-arg constructor for CDI. */
    public Scenario44Test() {
    }

    @Test
    public void configuredXaDataSourceClassIsUsed() {
        int constructionsBefore = CountingXaDataSource.CONSTRUCTION_COUNT.get();
        int xaConnectionsBefore = CountingXaDataSource.XA_CONNECTION_COUNT.get();

        service.persistOne();

        assertThat(CountingXaDataSource.CONSTRUCTION_COUNT.get())
                .as("the configured XADataSource class must have been instantiated by the resolver")
                .isGreaterThan(constructionsBefore - 1);
        assertThat(CountingXaDataSource.XA_CONNECTION_COUNT.get() - xaConnectionsBefore)
                .as("Hibernate's connection acquire must have gone through the configured XADataSource")
                .isGreaterThan(0);
    }
}
