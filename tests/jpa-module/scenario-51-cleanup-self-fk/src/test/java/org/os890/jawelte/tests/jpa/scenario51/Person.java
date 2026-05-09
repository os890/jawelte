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
package org.os890.jawelte.tests.jpa.scenario51;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * Self-referencing entity: child rows hold a FK to parent rows of
 * the same table. The {@code person} table can't be cleaned by
 * naive {@code DELETE FROM person} (children block parent
 * deletion); the TRUNCATE-with-RI-off strategy handles it.
 */
@Entity
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Person parent;

    /** Default no-arg constructor required by JPA. */
    public Person() {
    }

    /**
     * Convenience constructor for a root person.
     *
     * @param name the name
     */
    public Person(String name) {
        this.name = name;
    }

    /**
     * Convenience constructor for a child person.
     *
     * @param name   the name
     * @param parent the parent
     */
    public Person(String name, Person parent) {
        this.name = name;
        this.parent = parent;
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
     * Get the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the parent person, possibly {@code null}.
     *
     * @return the parent or null
     */
    public Person getParent() {
        return parent;
    }
}
