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
package org.os890.jawelte.tests.jpa.scenario48;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Outermost service (level 1); persists at level 1, then calls middle. */
@ApplicationScoped
public class OuterLevelService {

    @Inject
    private EntityManager entityManager;

    @Inject
    private MiddleLevelService middleLevelService;

    /** No-arg constructor for CDI. */
    public OuterLevelService() {
    }

    /**
     * Drive the three-level nesting: persist on level 1, call into
     * middle (which persists on level 2 and calls innermost which
     * persists on level 3).
     *
     * @param level1Name name persisted at level 1
     * @param level2Name name persisted at level 2
     * @param level3Name name persisted at level 3
     */
    @Transactional
    public void persistThreeLevels(String level1Name, String level2Name, String level3Name) {
        entityManager.persist(new Person(level1Name));
        middleLevelService.persistAtLevel2AndRecurse(level2Name, level3Name);
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
