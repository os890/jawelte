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
package org.os890.jawelte.tests.jpa.scenario04;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code @PersistenceContext(unitName="testPU04a")} is rewritten to
 * {@code @Inject @Named("testPU04a")}. With two PUs declared, jpa-module's
 * synthetic beans are {@code @Named}-only; the {@code @Named} qualifier added
 * during rewriting is what makes the field resolve.
 */
@EnableTestBeans
public class Scenario04Test {

    @Inject
    private RewriteSubject rewriteSubject;

    /** No-arg constructor for CDI. */
    public Scenario04Test() {
    }

    /** Both @PersistenceContext(unitName=…) fields route to distinct PU-specific beans. */
    @Test
    public void namedPersistenceContextRewritingRoutesToCorrectPu() {
        assertThat(rewriteSubject.getEntityManagerA())
                .as("@PersistenceContext(unitName=\"testPU04a\") must be rewritten to "
                        + "@Inject @Named(\"testPU04a\") and resolve")
                .isNotNull();
        assertThat(rewriteSubject.getEntityManagerB())
                .as("@PersistenceContext(unitName=\"testPU04b\") must be rewritten to "
                        + "@Inject @Named(\"testPU04b\") and resolve")
                .isNotNull();
        assertThat(rewriteSubject.getEntityManagerA())
                .as("the two @Named-rewritten EMs must be distinct proxies (different PUs)")
                .isNotSameAs(rewriteSubject.getEntityManagerB());
    }
}
