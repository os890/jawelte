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
package org.os890.jawelte.tests.jpa.scenario24;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Service that writes into both persistence units inside one @Transactional. */
@ApplicationScoped
public class CrossPuService {

    @Inject
    @Named("testPU24a")
    private EntityManager entityManagerA;

    @Inject
    @Named("testPU24b")
    private EntityManager entityManagerB;

    /** No-arg constructor for CDI. */
    public CrossPuService() {
    }

    /**
     * Persist one row into each PU's database. Both per-PU EntityTransactions
     * commit independently when the @Transactional method returns — RESOURCE_LOCAL
     * has no XA, so commit phase is best-effort flush-all-then-commit-all
     * (see DefaultResourceLocalTransactionStrategy).
     */
    @Transactional
    public void persistIntoBothPus() {
        entityManagerA.persist(new MarkerA());
        entityManagerB.persist(new MarkerB());
    }

    /**
     * Count rows in PU "a".
     *
     * @return the row count
     */
    @Transactional
    public long countInPuA() {
        return entityManagerA
                .createQuery("SELECT COUNT(m) FROM MarkerA m", Long.class)
                .getSingleResult();
    }

    /**
     * Count rows in PU "b".
     *
     * @return the row count
     */
    @Transactional
    public long countInPuB() {
        return entityManagerB
                .createQuery("SELECT COUNT(m) FROM MarkerB m", Long.class)
                .getSingleResult();
    }
}
