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
package org.os890.jawelte.tests.lnp.scenario07.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Standalone Customer entity used by scenario-06's roundtrip
 * scenario. No relations — keeps the schema, the seed, and the
 * expected response payloads small so the roundtrip's measurement
 * signal isn't drowned by entity-graph cost.
 */
@Entity
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String email;

    /** Default constructor required by JPA. */
    public Customer() {
    }

    /** @return the primary key, assigned by H2's IDENTITY column */
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

    /** @return the customer email */
    public String getEmail() {
        return email;
    }

    /** @param email the customer email */
    public void setEmail(String email) {
        this.email = email;
    }
}
