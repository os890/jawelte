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
package org.os890.jawelte.tests.testcontrol.scenario08;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Minimal entity owned by the {@code testcontrolScenario08OrdersPU}
 * persistence unit. The table name is {@code CUSTOMER_ORDER} (not
 * {@code ORDER}) because {@code ORDER} is a reserved word in H2's
 * SQL dialect.
 */
@Entity
@Table(name = "CUSTOMER_ORDER")
public class Order {

    @Id
    private Integer id;

    @Column(name = "CUSTOMER_ID")
    private Integer customerId;

    @Column(name = "TOTAL_CENTS")
    private Integer totalCents;

    public Order() {
    }

    public Integer getId() {
        return id;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public Integer getTotalCents() {
        return totalCents;
    }
}
