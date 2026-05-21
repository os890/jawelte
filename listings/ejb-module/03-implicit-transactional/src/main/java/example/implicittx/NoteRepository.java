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
package example.implicittx;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * No @Transactional anywhere. ejb-module's interceptor wraps every
 * method of a @Stateless / @Singleton bean in an implicit
 * REQUIRED-style transaction; persist commits without the
 * @Transactional annotation that the same code would need under
 * plain jpa-module.
 */
@Stateless
public class NoteRepository {

    @Inject
    private EntityManager entityManager;

    public Long save(String body) {
        Note note = new Note(body);
        entityManager.persist(note);
        return note.getId();
    }

    public long count() {
        return entityManager
                .createQuery("SELECT COUNT(n) FROM Note n", Long.class)
                .getSingleResult();
    }
}
