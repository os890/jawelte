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

import static org.mockito.Mockito.mock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;

/**
 * User-supplied {@code @Produces EntityManager}. jpa-module's
 * {@code JpaCdiExtension} detects the test-classpath EM producer and backs
 * off — the synthetic addon EM proxy is not registered, so the user's mock
 * is what reaches every {@code @Inject EntityManager} site.
 *
 * <p>(The scenario module folder is named "test-bean-static-field-..." after
 * the POC's reference but the static-field {@code @TestBean} mode collides with
 * jpa-module's @Default synthetic bean today — see §6 of
 * {@code tickets/poc-gaps-2nd-pass.html} for the underlying gap. The
 * {@code @Produces} producer-method form locks in the supported user-override
 * path.)
 */
@ApplicationScoped
public class UserEmProducer {

    /** Default constructor required by CDI. */
    public UserEmProducer() {
    }

    /** @return a Mockito mock that the test asserts on */
    @Produces
    @ApplicationScoped
    public EntityManager userEntityManager() {
        return mock(EntityManager.class);
    }
}
