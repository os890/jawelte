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
package org.os890.jawelte.tests.jpa.scenario75;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Stands in for the migration tool, writing to the table Flyway keeps
 * its record in.
 *
 * <p>Created and written through native SQL rather than as an
 * {@code @Entity}, because that is how it exists in a real deployment:
 * no application maps it, and cleanup only knows about it because it
 * works from the schema's real tables rather than from the metamodel -
 * which is precisely why it was in scope for emptying.
 */
@ApplicationScoped
public class SchemaHistory {

    static final String TABLE = "flyway_schema_history";

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor required by CDI. */
    public SchemaHistory() {
    }

    /** Apply the "migration": create the table and record that it ran. */
    @Transactional
    public void migrate() {
        entityManager.createNativeQuery(
                "CREATE TABLE IF NOT EXISTS " + TABLE
                        + " (installed_rank INT PRIMARY KEY, script VARCHAR(255))").executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO " + TABLE + " (installed_rank, script) VALUES (1, 'V1__create_note.sql')")
                .executeUpdate();
    }

    /**
     * @return how many migrations the history says have been applied
     */
    @Transactional
    public long appliedCount() {
        return ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM " + TABLE)
                .getSingleResult()).longValue();
    }

    /**
     * @param note a row to store in the ordinary table
     */
    @Transactional
    public void writeNote(Note note) {
        entityManager.persist(note);
    }

    /**
     * @return how many rows the ordinary table holds
     */
    @Transactional
    public long noteCount() {
        return entityManager.createQuery("SELECT COUNT(n) FROM Note n", Long.class).getSingleResult();
    }
}
