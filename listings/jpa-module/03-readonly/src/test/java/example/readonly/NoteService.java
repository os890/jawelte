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
package example.readonly;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

@ApplicationScoped
public class NoteService {

    @Inject
    private EntityManager entityManager;

    @Transactional
    public Long persistNote(String text) {
        Note note = new Note(text);
        entityManager.persist(note);
        return note.getId();
    }

    /**
     * @Transactional @ReadOnly — the persist + flush succeed in-memory
     * during the method (so the id is assigned), but jpa-module's
     * interceptor marks the transaction rollback-only, so the row
     * never reaches the database.
     */
    @Transactional
    @ReadOnly
    public Long tryToPersistButDiscard(String text) {
        Note note = new Note(text);
        entityManager.persist(note);
        entityManager.flush();
        return note.getId();
    }

    @Transactional
    public long count() {
        return entityManager.createQuery("SELECT COUNT(n) FROM Note n", Long.class).getSingleResult();
    }
}
