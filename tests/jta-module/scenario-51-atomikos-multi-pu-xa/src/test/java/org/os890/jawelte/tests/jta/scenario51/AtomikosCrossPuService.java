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
package org.os890.jawelte.tests.jta.scenario51;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Service writing to both PUs inside one Atomikos-driven JTA tx. */
@ApplicationScoped
public class AtomikosCrossPuService {

    @Inject
    @Named("testJtaPU51a")
    private EntityManager entityManagerA;

    @Inject
    @Named("testJtaPU51b")
    private EntityManager entityManagerB;

    /** No-arg constructor for CDI. */
    public AtomikosCrossPuService() {
    }

    /** Persist one row into each PU inside a single JTA tx. */
    @Transactional
    public void persistIntoBothPus() {
        entityManagerA.persist(new AtomikosMarkerA());
        entityManagerB.persist(new AtomikosMarkerB());
    }

    /**
     * Count rows in PU "a".
     *
     * @return row count
     */
    @Transactional
    public long countInPuA() {
        return entityManagerA
                .createQuery("SELECT COUNT(m) FROM AtomikosMarkerA m", Long.class)
                .getSingleResult();
    }

    /**
     * Count rows in PU "b".
     *
     * @return row count
     */
    @Transactional
    public long countInPuB() {
        return entityManagerB
                .createQuery("SELECT COUNT(m) FROM AtomikosMarkerB m", Long.class)
                .getSingleResult();
    }
}
