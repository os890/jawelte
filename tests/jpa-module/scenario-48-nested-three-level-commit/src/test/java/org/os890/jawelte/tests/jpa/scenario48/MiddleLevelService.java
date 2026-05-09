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

/** Middle service (level 2); persists at level 2, then calls innermost. */
@ApplicationScoped
public class MiddleLevelService {

    @Inject
    private EntityManager entityManager;

    @Inject
    private InnerLevelService innerLevelService;

    /** No-arg constructor for CDI. */
    public MiddleLevelService() {
    }

    /**
     * Persist a level-2 {@link Person} and recurse into level 3.
     *
     * @param level2Name the name persisted on level 2
     * @param level3Name the name persisted on level 3
     */
    @Transactional
    public void persistAtLevel2AndRecurse(String level2Name, String level3Name) {
        entityManager.persist(new Person(level2Name));
        innerLevelService.persistAtLevel3(level3Name);
    }
}
