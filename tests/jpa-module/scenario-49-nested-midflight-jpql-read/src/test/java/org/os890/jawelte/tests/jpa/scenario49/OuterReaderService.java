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
package org.os890.jawelte.tests.jpa.scenario49;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Outer service that reads inner's committed data via JPQL after
 * the inner @Transactional returns.
 */
@ApplicationScoped
public class OuterReaderService {

    @Inject
    private EntityManager entityManager;

    @Inject
    private InnerWriterService innerWriterService;

    /** No-arg constructor for CDI. */
    public OuterReaderService() {
    }

    /**
     * Drive the nested flow. Inner persists + commits its row;
     * outer then runs a JPQL count which bypasses Hibernate's L1
     * cache and queries the DB directly — sees inner's committed
     * data.
     *
     * @param innerName name persisted by the inner level
     * @return the row count outer reads after inner commits
     */
    @Transactional
    public long readMidFlightAfterInnerCommit(String innerName) {
        innerWriterService.persist(innerName);
        // JPQL count goes through the DB, not the L1 cache, so the
        // outer EM sees inner's committed row even though our impl
        // does not call em.clear() after the inner pop.
        return entityManager
                .createQuery("SELECT COUNT(p) FROM Person p", Long.class)
                .getSingleResult();
    }

    /**
     * Three-level flow: persist L1-A on the outer EM, dispatch to
     * inner which persists L2-B in its own committed tx, then
     * persist L1-C on the outer EM. Returns the JPQL count read on
     * the outer EM <em>between</em> the inner commit and the outer's
     * tail persist. After this method returns and the outer commits,
     * all three rows live in the DB.
     *
     * @return the mid-flight row count observed by the outer EM
     */
    @Transactional
    public long threeLevelMidFlightThenTailPersist() {
        entityManager.persist(new Person("L1-A"));
        innerWriterService.persist("L2-B");
        long midFlightCount = entityManager
                .createQuery("SELECT COUNT(p) FROM Person p", Long.class)
                .getSingleResult();
        entityManager.persist(new Person("L1-C"));
        return midFlightCount;
    }

    /**
     * Total {@link Person} row count.
     *
     * @return the row count
     */
    @Transactional
    public long countPeople() {
        return entityManager
                .createQuery("SELECT COUNT(p) FROM Person p", Long.class)
                .getSingleResult();
    }
}
