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
package org.os890.jawelte.tests.springdata.scenario14;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Second entity — backs {@link OrderRepository}. */
@Entity
@Table(name = "ORDERS")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    /** Default no-arg constructor required by JPA. */
    public Order() {
    }

    /**
     * Convenience constructor.
     *
     * @param description the order description
     */
    public Order(String description) {
        this.description = description;
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
     * Get the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}
