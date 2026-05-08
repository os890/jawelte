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
package org.os890.jawelte.tests.jpa.scenario61;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Persists a {@link Foo}/{@link Bar} pair with mutual FK references. */
@ApplicationScoped
public class CycleService {

    @Inject
    private EntityManager entityManager;

    /** Default constructor for CDI. */
    public CycleService() {
    }

    /**
     * Persist a Foo + Bar pair where each references the other:
     * insert Foo without bar_id, insert Bar pointing at Foo, then
     * update Foo to point at Bar. After the @Transactional commits,
     * both rows exist with mutual FK references — neither table can
     * be deleted in isolation without nulling at least one FK first.
     */
    @Transactional
    public void persistCycle() {
        Foo foo = new Foo();
        entityManager.persist(foo);
        entityManager.flush();
        Bar bar = new Bar();
        bar.setFoo(foo);
        entityManager.persist(bar);
        entityManager.flush();
        foo.setBar(bar);
        entityManager.flush();
    }

    /** @return the row count of the {@code Foo} table. */
    @Transactional
    public long countFoo() {
        return entityManager.createQuery("SELECT COUNT(f) FROM Foo f", Long.class).getSingleResult();
    }

    /** @return the row count of the {@code Bar} table. */
    @Transactional
    public long countBar() {
        return entityManager.createQuery("SELECT COUNT(b) FROM Bar b", Long.class).getSingleResult();
    }
}
