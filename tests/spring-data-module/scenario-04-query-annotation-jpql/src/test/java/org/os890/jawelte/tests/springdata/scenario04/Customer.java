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
package org.os890.jawelte.tests.springdata.scenario04;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Queried by JPQL provided via {@code @Query} on the repository method. */
@Entity
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String status;

    /** Default no-arg constructor required by JPA. */
    public Customer() {
    }

    /**
     * Convenience constructor.
     *
     * @param name the customer's name
     * @param status the customer's status
     */
    public Customer(String name, String status) {
        this.name = name;
        this.status = status;
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
     * Get the customer's name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the customer's status.
     *
     * @return the status
     */
    public String getStatus() {
        return status;
    }
}
