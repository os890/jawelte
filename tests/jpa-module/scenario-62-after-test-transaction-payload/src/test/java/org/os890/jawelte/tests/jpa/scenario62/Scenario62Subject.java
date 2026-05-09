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
package org.os890.jawelte.tests.jpa.scenario62;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Subject driven by {@link Scenario62Test} via {@code EngineTestKit}
 * — the outer test inspects the {@link AfterTestTxRecorder} static
 * state after the kit run.
 *
 * <p>Method ordering is alphabetical (deterministic). Both methods
 * are {@code @Transactional} so the lifecycle adapter wraps them
 * and fires {@code AfterTestTransaction} on each afterEach. The
 * {@code aPass…} method completes normally → the wrapping commits;
 * the {@code bThrow…} method raises a {@code RuntimeException}
 * inside the body → the wrapping rolls back. Together they exercise
 * both branches of the §5.1 fix.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.MethodName.class)
public class Scenario62Subject {

    @Inject
    private EntityManager entityManager;

    /** Default constructor required by CDI / JUnit. */
    public Scenario62Subject() {
    }

    /** Pass path: persist + return normally → wrapping tx commits. */
    @Test
    @Transactional
    public void aPassingTransactional() {
        entityManager.persist(new Marker());
    }

    /** Rollback path: persist then throw → wrapping tx rolls back. */
    @Test
    @Transactional
    public void bThrowingTransactional() {
        entityManager.persist(new Marker());
        throw new RuntimeException(
                "scenario-62: intentional throw to drive AfterTestTransaction.committed = false");
    }
}
