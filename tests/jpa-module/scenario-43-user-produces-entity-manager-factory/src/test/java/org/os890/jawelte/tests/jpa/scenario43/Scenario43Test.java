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
package org.os890.jawelte.tests.jpa.scenario43;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * A user-declared {@code @Produces EntityManagerFactory} on the test classpath
 * triggers jpa-module's per-PU back-off: the synthetic addon EMF bean is not
 * registered for that PU and the user's instance reaches every
 * {@code @Inject EntityManagerFactory} site.
 */
@EnableTestBeans
public class Scenario43Test {

    @Inject
    private EntityManagerFactory injectedEntityManagerFactory;

    /** No-arg constructor for CDI. */
    public Scenario43Test() {
    }

    /** The mock's default isOpen() is false; the real EMF would return true. */
    @Test
    public void userProducedEmfWinsOverAddon() {
        // Mockito mocks default isOpen() to false; jpa-module's real EMF would
        // return true. A false return here proves the user's @Produces won.
        assertThat(injectedEntityManagerFactory.isOpen())
                .as("user @Produces EntityManagerFactory must shadow the addon's EMF — "
                        + "the mock returns false on isOpen() while a real Hibernate EMF returns true")
                .isFalse();
    }
}
