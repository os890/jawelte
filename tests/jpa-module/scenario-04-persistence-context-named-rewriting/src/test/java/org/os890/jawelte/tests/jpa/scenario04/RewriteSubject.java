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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * CDI bean with two {@code @PersistenceContext(unitName=…)} fields. The
 * {@code JpaCdiExtension} rewriting adds {@code @Inject @Named(unitName)}
 * to each, so the two fields route to the two synthetic PU-specific
 * EntityManager beans.
 */
@ApplicationScoped
public class RewriteSubject {

    @PersistenceContext(unitName = "testPU04a")
    private EntityManager entityManagerA;

    @PersistenceContext(unitName = "testPU04b")
    private EntityManager entityManagerB;

    /** No-arg constructor required by CDI. */
    public RewriteSubject() {
    }

    /** @return the EM rewritten with @Named("testPU04a") */
    public EntityManager getEntityManagerA() {
        return entityManagerA;
    }

    /** @return the EM rewritten with @Named("testPU04b") */
    public EntityManager getEntityManagerB() {
        return entityManagerB;
    }
}
