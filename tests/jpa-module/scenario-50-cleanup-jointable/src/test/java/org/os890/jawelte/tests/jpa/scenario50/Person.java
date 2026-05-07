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

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

/** Owning side of the M:N relationship to {@link Hobby}. */
@Entity
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(
            name = "person_hobby",
            joinColumns = @JoinColumn(name = "person_id"),
            inverseJoinColumns = @JoinColumn(name = "hobby_id"))
    private List<Hobby> hobbies = new ArrayList<>();

    /** Default no-arg constructor required by JPA. */
    public Person() {
    }

    /**
     * Convenience constructor.
     *
     * @param name the person's name
     */
    public Person(String name) {
        this.name = name;
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
     * Get the mutable list of hobbies. Adding to this list and
     * persisting the {@link Person} cascades the persist to each
     * new {@link Hobby} (per {@link CascadeType#PERSIST}) and
     * inserts join-table rows.
     *
     * @return the hobby list
     */
    public List<Hobby> getHobbies() {
        return hobbies;
    }
}
