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
package org.os890.jawelte.tests.jpa.scenario27;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * The "subject" test class — driven via JUnit Platform Test Kit by
 * {@link Scenario27Test}. Surefire's default {@code *Test.java} filter
 * excludes this class (no {@code Test} suffix) so it does not run during
 * the normal test run.
 *
 * <p>Annotated with {@code @PersistenceConfig(fileMode = true)} and
 * <em>no</em> {@code filePath} — exercising
 * {@code JpaLifecycleAdapter.resolveFileModePath}'s default-path branch
 * which resolves to {@code ~/<TestClass>_db}.
 */
@EnableTestBeans
@PersistenceConfig(fileMode = true)
public class Scenario27FileModeSubject {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor required by JUnit / CDI. */
    public Scenario27FileModeSubject() {
    }

    /** Persist + flush so H2 actually writes the .mv.db file at the default path. */
    @Test
    @Transactional
    public void persistOneRowToFileModeDefaultPath() {
        entityManager.persist(new Marker());
        entityManager.flush();
    }
}
