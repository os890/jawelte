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
package org.os890.jawelte.tests.jpa.scenario03;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;


import io.quarkus.test.junit.QuarkusTest;
/**
 * A field annotated only with {@code @PersistenceContext} (no {@code @Inject})
 * is rewritten by jpa-module's {@code JpaCdiExtension} to {@code @Inject}.
 * The rewritten field receives jpa-module's transaction-scoped EM proxy.
 */
@EnableTestBeans
@QuarkusTest
public class Scenario03Test {

    @Inject
    private RewriteSubject rewriteSubject;

    /** No-arg constructor for CDI. */
    public Scenario03Test() {
    }

    /** @PersistenceContext-only field is non-null after CDI bean construction. */
    @Test
    public void persistenceContextFieldIsRewrittenAndPopulated() {
        assertThat(rewriteSubject.getEntityManager())
                .as("@PersistenceContext field must be populated by jpa-module's "
                        + "ProcessAnnotatedType rewriting (adds @Inject)")
                .isNotNull();
    }
}
