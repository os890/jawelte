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
package org.os890.jawelte.tests.jpa.scenario61;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * One half of a two-table FK cycle ({@code Foo.bar_id → Bar.id},
 * {@code Bar.foo_id → Foo.id}). The cycle is the canonical case
 * where reverse-order DELETE alone fails: deleting Foo first is
 * blocked by Bar's foo_id FK; deleting Bar first is blocked by Foo's
 * bar_id FK. Pass 1's null-update of nullable FK columns breaks the
 * cycle so Pass 2's reverse DELETE succeeds.
 */
@Entity
public class Foo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "bar_id")
    private Bar bar;

    /** Default constructor for JPA. */
    public Foo() {
    }

    /** @return the database-assigned id (null until persist). */
    public Long getId() {
        return id;
    }

    /** @return the linked Bar (may be null). */
    public Bar getBar() {
        return bar;
    }

    /**
     * Set the linked Bar.
     *
     * @param bar the Bar to link
     */
    public void setBar(Bar bar) {
        this.bar = bar;
    }
}
