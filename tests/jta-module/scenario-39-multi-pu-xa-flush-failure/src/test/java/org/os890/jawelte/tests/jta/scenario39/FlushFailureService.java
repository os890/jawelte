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
package org.os890.jawelte.tests.jta.scenario39;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Service that drives a flush failure in one PU during a multi-PU JTA tx. */
@ApplicationScoped
public class FlushFailureService {

    @Inject
    @Named("testJtaPU39a")
    private EntityManager entityManagerA;

    @Inject
    @Named("testJtaPU39b")
    private EntityManager entityManagerB;

    /** No-arg constructor for CDI. */
    public FlushFailureService() {
    }

    /**
     * Persist a valid row to PU "a" and a NOT-NULL-violating row to
     * PU "b". PU "b"'s flush fails at JTA commit; XA atomicity must
     * roll back both PUs.
     */
    @Transactional
    public void writeBothPusBFlushFails() {
        entityManagerA.persist(new MarkerA());
        MarkerB invalid = new MarkerB();
        invalid.setData(null);
        entityManagerB.persist(invalid);
    }

    /**
     * Count rows in PU "a".
     *
     * @return row count
     */
    @Transactional
    public long countInPuA() {
        return entityManagerA.createQuery("SELECT COUNT(m) FROM MarkerA m", Long.class)
                .getSingleResult();
    }

    /**
     * Count rows in PU "b".
     *
     * @return row count
     */
    @Transactional
    public long countInPuB() {
        return entityManagerB.createQuery("SELECT COUNT(m) FROM MarkerB m", Long.class)
                .getSingleResult();
    }
}
