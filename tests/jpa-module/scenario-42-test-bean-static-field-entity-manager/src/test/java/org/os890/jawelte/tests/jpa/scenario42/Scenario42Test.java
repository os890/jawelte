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
package org.os890.jawelte.tests.jpa.scenario42;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * A user-declared {@code @Produces EntityManager} on the test classpath
 * triggers jpa-module's per-PU back-off: the synthetic addon EM proxy is not
 * registered for that PU and the user's instance reaches every
 * {@code @Inject EntityManager} site.
 *
 * <p>(The scenario folder name "test-bean-static-field-entity-manager" mirrors
 * the POC label, but the static-field {@code @TestBean} form is currently
 * blocked by an ambiguous-resolution conflict with the addon's @Default
 * synthetic bean — captured in §6 of
 * {@code tickets/poc-gaps-2nd-pass.html}. The producer-method form below is
 * the supported override path today.)
 */
@EnableTestBeans
public class Scenario42Test {

    @Inject
    private EntityManager injectedEntityManager;

    /** No-arg constructor for CDI. */
    public Scenario42Test() {
    }

    /** The user's mock returns false on isOpen(); the real proxy would behave differently. */
    @Test
    public void userProducedEntityManagerWinsOverAddon() {
        assertThat(injectedEntityManager.isOpen())
                .as("user @Produces EntityManager must shadow the addon's synthetic EM — "
                        + "the mock returns false on isOpen() while the real proxy would surface "
                        + "the underlying EM's state (or throw outside any tx)")
                .isFalse();
    }
}
