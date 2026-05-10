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
package org.os890.jawelte.tests.jta.scenario39;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Entity in PU "b" with a NOT-NULL constraint on {@code data}. The
 * scenario persists an instance with {@code data == null} to drive a
 * flush-time SQL constraint violation in PU "b" — XA atomicity
 * requires PU "a"'s commit to roll back too.
 */
@Entity
public class MarkerB {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String data;

    /** Default no-arg constructor required by JPA. */
    public MarkerB() {
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
     * Get the (NOT-NULL) data field.
     *
     * @return the data
     */
    public String getData() {
        return data;
    }

    /**
     * Set the data field. {@code null} drives a flush-time constraint
     * violation in PU "b".
     *
     * @param newData the new value
     */
    public void setData(String newData) {
        this.data = newData;
    }
}
