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
package org.os890.jawelte.tests.dbtestdata.scenario67;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Minimal entity for the DbSeed-under-JTA reproducer. */
@Entity
public class Customer {

    @Id
    private Long id;

    private String name;

    /** Default constructor for JPA. */
    public Customer() {
    }

    /** @return the primary key */
    public Long getId() {
        return id;
    }

    /** @param id the primary key */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return the customer name */
    public String getName() {
        return name;
    }

    /** @param name the customer name */
    public void setName(String name) {
        this.name = name;
    }
}
