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
package org.os890.jawelte.tests.jpa.scenario05;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;


import io.quarkus.test.junit.QuarkusTest;
/**
 * A field annotated only with {@code @PersistenceUnit} (no {@code @Inject})
 * is rewritten by jpa-module's {@code JpaCdiExtension} to {@code @Inject}.
 * The rewritten field receives jpa-module's synthetic EntityManagerFactory.
 */
@EnableTestBeans
@QuarkusTest
public class Scenario05Test {

    @Inject
    private RewriteSubject rewriteSubject;

    /** No-arg constructor for CDI. */
    public Scenario05Test() {
    }

    /** @PersistenceUnit-only field is non-null and reports an open EMF. */
    @Test
    public void persistenceUnitFieldIsRewrittenAndPopulated() {
        assertThat(rewriteSubject.getEntityManagerFactory())
                .as("@PersistenceUnit field must be populated by jpa-module's "
                        + "ProcessAnnotatedType rewriting (adds @Inject)")
                .isNotNull();
        assertThat(rewriteSubject.getEntityManagerFactory().isOpen())
                .as("the rewritten EMF must be the framework-managed JVM-cached one (open)")
                .isTrue();
    }
}
