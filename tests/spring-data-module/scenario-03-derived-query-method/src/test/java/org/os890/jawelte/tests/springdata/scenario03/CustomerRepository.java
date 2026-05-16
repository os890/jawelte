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
package org.os890.jawelte.tests.springdata.scenario03;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository with a single derived-query method; Spring Data generates JPQL from the method name. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Spring Data parses the name and generates JPQL
     * {@code SELECT c FROM Customer c WHERE c.name = ?1}.
     *
     * @param name the name to match exactly
     * @return matching customers (empty when none)
     */
    List<Customer> findByName(String name);
}
