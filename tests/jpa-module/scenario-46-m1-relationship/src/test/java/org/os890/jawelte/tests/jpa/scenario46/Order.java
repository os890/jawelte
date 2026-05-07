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
package org.os890.jawelte.tests.jpa.scenario46;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Child entity in the M:1 relationship. */
@Entity
@Table(name = "ORDERS")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Default no-arg constructor required by JPA. */
    public Order() {
    }

    /**
     * Convenience constructor.
     *
     * @param description the order description
     * @param customer    the customer placing the order
     */
    public Order(String description, Customer customer) {
        this.description = description;
        this.customer = customer;
    }

    /**
     * Get the database-assigned id.
     *
     * @return the id, or {@code null} before persistence
     */
    public Long getId() {
        return id;
    }

    /**
     * Get the order description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the order description.
     *
     * @param description the new description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the customer who placed the order.
     *
     * @return the customer, possibly {@code null}
     */
    public Customer getCustomer() {
        return customer;
    }

    /**
     * Set the customer.
     *
     * @param customer the customer
     */
    public void setCustomer(Customer customer) {
        this.customer = customer;
    }
}
