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

/** Other half of the two-table FK cycle — see {@link Foo}. */
@Entity
public class Bar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "foo_id")
    private Foo foo;

    /** Default constructor for JPA. */
    public Bar() {
    }

    /** @return the database-assigned id (null until persist). */
    public Long getId() {
        return id;
    }

    /** @return the linked Foo (may be null). */
    public Foo getFoo() {
        return foo;
    }

    /**
     * Set the linked Foo.
     *
     * @param foo the Foo to link
     */
    public void setFoo(Foo foo) {
        this.foo = foo;
    }
}
