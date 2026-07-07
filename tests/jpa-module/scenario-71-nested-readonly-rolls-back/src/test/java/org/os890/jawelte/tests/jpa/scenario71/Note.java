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
package org.os890.jawelte.tests.jpa.scenario71;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Minimal entity for the nested-@ReadOnly rollback test. */
@Entity
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String text;

    /** Default no-arg constructor required by JPA. */
    public Note() {
    }

    /**
     * Convenience constructor.
     *
     * @param text the initial text
     */
    public Note(String text) {
        this.text = text;
    }

    /**
     * Get the database-assigned id.
     *
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * Get the current text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }
}
