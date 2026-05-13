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
package org.os890.jawelte.tests.dbtestdata.scenario36a;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.dbtestdata.api.DbSeed;

/**
 * Two persistence units active simultaneously: touching both EMs
 * inside a {@code @Transactional} method pushes "testPU36aA" and
 * "testPU36aB" onto the calling thread's stack. The no-arg
 * {@code DbSeed.forPersistenceUnit()} call cannot disambiguate
 * between them and the active resolver raises
 * {@link IllegalStateException}.
 */
@ApplicationScoped
public class AmbiguousPuSeedingService {

    @PersistenceContext(unitName = "testPU36aA")
    private EntityManager entityManagerA;

    @PersistenceContext(unitName = "testPU36aB")
    private EntityManager entityManagerB;

    public AmbiguousPuSeedingService() {
    }

    @Transactional
    public void touchBothPusThenAttemptDefaultPuSeed() {
        // Force both proxies through peekOrAutoBegin so the two
        // PUs both join the current @Transactional frame.
        entityManagerA.createNativeQuery("SELECT 1").getSingleResult();
        entityManagerB.createNativeQuery("SELECT 1").getSingleResult();
        DbSeed.forPersistenceUnit()
                .datasetContent("<dataset/>")
                .cleanInsert()
                .execute();
    }
}
