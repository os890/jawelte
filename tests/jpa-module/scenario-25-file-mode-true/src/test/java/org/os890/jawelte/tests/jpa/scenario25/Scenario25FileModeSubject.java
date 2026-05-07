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
package org.os890.jawelte.tests.jpa.scenario25;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * The "subject" test class — driven via JUnit Platform Test Kit by
 * {@link Scenario25Test}. Surefire's default {@code *Test.java}
 * filter excludes this class (no {@code Test} suffix) so it does
 * not run during the normal test run.
 *
 * <p>Annotated with {@code @PersistenceConfig(fileMode = true)},
 * which engages the "first method runs, every subsequent method is
 * aborted" debug mode. Two ordered {@code @Test} methods append to
 * a static list; after the kit run the list must hold exactly the
 * first method's name.
 */
@EnableTestBeans
@PersistenceConfig(fileMode = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario25FileModeSubject {

    /** Records the simple name of every method that started execution. */
    public static final List<String> EXECUTED_METHODS = Collections.synchronizedList(new ArrayList<>());

    @Inject
    private MarkerService markerService;

    /** No-arg constructor required by JUnit / CDI. */
    public Scenario25FileModeSubject() {
    }

    /** First method — actually runs and writes a marker into the H2 file. */
    @Test
    @Order(1)
    public void firstMethodPersists() {
        EXECUTED_METHODS.add("firstMethodPersists");
        markerService.persist("file-mode-first");
    }

    /**
     * Second method — must NOT run. {@code JpaLifecycleAdapter.beforeEach}
     * raises {@code TestAbortedException} because the first method has
     * already executed.
     */
    @Test
    @Order(2)
    public void secondMethodMustBeAborted() {
        EXECUTED_METHODS.add("secondMethodMustBeAborted");
    }
}
