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
package org.os890.jawelte.tests.jpa.scenario55;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Outer layer: writable {@code @Transactional} that persists two
 * notes around a nested {@code @ReadOnly} call. The outer's writes
 * commit normally, the inner's writes get rolled back.
 */
@ApplicationScoped
public class OuterWritableService {

    @Inject
    private EntityManager entityManager;

    @Inject
    private InnerReadOnlyService innerReadOnlyService;

    /** Default constructor required by CDI. */
    public OuterWritableService() {
    }

    /**
     * Persist one {@link Note}, run the @ReadOnly inner call, then
     * persist a second {@link Note}. The two outer persists must
     * commit; the inner call's persist + setter must not.
     *
     * @param idToMutate the existing-note id passed to the inner
     * @param newText    the (discarded) text the inner tries to apply
     */
    @Transactional
    public void writeAroundReadOnlyInner(Long idToMutate, String newText) {
        entityManager.persist(new Note("outer-write-before"));
        innerReadOnlyService.mutateAndInsertUnderReadOnly(idToMutate, newText);
        entityManager.persist(new Note("outer-write-after"));
    }

    /**
     * Persist a {@link Note} with the given text and return its id.
     *
     * @param text the initial text
     * @return the assigned id
     */
    @Transactional
    public Long seed(String text) {
        Note note = new Note(text);
        entityManager.persist(note);
        entityManager.flush();
        return note.getId();
    }

    /**
     * Total {@link Note} row count.
     *
     * @return the count
     */
    @Transactional
    public long countNotes() {
        return entityManager
                .createQuery("SELECT COUNT(n) FROM Note n", Long.class)
                .getSingleResult();
    }

    /**
     * Read the current text of a {@link Note} by id.
     *
     * @param id the note id
     * @return the persisted text
     */
    @Transactional
    public String currentText(Long id) {
        return entityManager.find(Note.class, id).getText();
    }
}
