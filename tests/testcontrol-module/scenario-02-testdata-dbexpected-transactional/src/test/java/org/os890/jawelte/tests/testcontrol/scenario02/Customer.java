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
package org.os890.jawelte.tests.testcontrol.scenario02;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Minimal entity whose CUSTOMER table jpa-module's auto-DDL creates. */
@Entity
public class Customer {

    @Id
    private Integer id;

    @Column(length = 64)
    private String name;

    public Customer() {
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
