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
package org.os890.jawelte.tests.jpa.scenario26;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * Without {@code fileMode = true} the bootstrap properties resolve
 * to an in-memory H2 URL keyed off the persistence-unit name —
 * {@code jdbc:h2:mem:testPU26;DB_CLOSE_DELAY=-1}. The resolved URL
 * is observable on the bootstrapped
 * {@link EntityManagerFactory}'s property map, so a test can pin
 * the format end-to-end.
 */
@EnableTestBeans
@PersistenceConfig
public class Scenario26Test {

    @Inject
    private EntityManagerFactory entityManagerFactory;

    /** No-arg constructor for CDI. */
    public Scenario26Test() {
    }

    /** Default (no fileMode) bootstrap produces the in-memory H2 URL. */
    @Test
    public void noFileModeYieldsInMemoryH2Url() {
        Object url = entityManagerFactory.getProperties().get("jakarta.persistence.jdbc.url");
        assertThat(url)
                .as("default bootstrap must produce an in-memory H2 URL keyed by PU name")
                .isEqualTo("jdbc:h2:mem:testPU26;DB_CLOSE_DELAY=-1");

        Object driver = entityManagerFactory.getProperties().get("jakarta.persistence.jdbc.driver");
        assertThat(driver)
                .as("driver class must be set explicitly so Hibernate's connection pool can resolve it")
                .isEqualTo("org.h2.Driver");
    }
}
