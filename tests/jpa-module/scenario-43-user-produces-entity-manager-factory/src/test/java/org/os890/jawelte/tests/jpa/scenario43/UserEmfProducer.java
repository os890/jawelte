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

import static org.mockito.Mockito.mock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManagerFactory;

/**
 * User-supplied {@code @Produces EntityManagerFactory}. jpa-module's
 * {@code JpaCdiExtension} detects a test-classpath EMF producer (via
 * {@code hasTestClasspathBean}) and backs off — the synthetic addon EMF
 * bean is not registered, so the user's mock is what reaches every
 * {@code @Inject EntityManagerFactory} injection point.
 */
@ApplicationScoped
public class UserEmfProducer {

    /** Default constructor required by CDI. */
    public UserEmfProducer() {
    }

    /** @return a Mockito mock that the test asserts on */
    @Produces
    @ApplicationScoped
    public EntityManagerFactory userEntityManagerFactory() {
        return mock(EntityManagerFactory.class);
    }
}
