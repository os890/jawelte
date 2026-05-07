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
package org.os890.jawelte.tests.jpa.scenario50;

import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.hibernate.Session;

/** Drives the M:N persist + counts; uses a native SQL count for the join table. */
@ApplicationScoped
public class PersonHobbyService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public PersonHobbyService() {
    }

    /**
     * Persist a {@link Person} with two {@link Hobby} entries
     * (cascade persist + join-table inserts).
     *
     * @param personName the person's name
     */
    @Transactional
    public void persistPersonWithTwoHobbies(String personName) {
        Person person = new Person(personName);
        person.getHobbies().add(new Hobby("running"));
        person.getHobbies().add(new Hobby("reading"));
        entityManager.persist(person);
    }

    /**
     * Count rows in the auto-generated join table via native SQL —
     * JPQL can't reach an unmapped table.
     *
     * @return the join-table row count
     */
    @Transactional
    public long countJoinTableRows() {
        Session session = entityManager.unwrap(Session.class);
        AtomicLong count = new AtomicLong();
        session.doWork(connection -> {
            try (java.sql.Statement statement = connection.createStatement();
                 java.sql.ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM person_hobby")) {
                if (resultSet.next()) {
                    count.set(resultSet.getLong(1));
                }
            }
        });
        return count.get();
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

    /**
     * Total {@link Hobby} row count.
     *
     * @return the row count
     */
    @Transactional
    public long countHobbies() {
        return entityManager
                .createQuery("SELECT COUNT(h) FROM Hobby h", Long.class)
                .getSingleResult();
    }
}
