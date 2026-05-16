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

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository with an explicit JPQL `@Query`-annotated method. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Look up customers in a given status via explicit JPQL.
     *
     * @param status the status filter
     * @return matching customers
     */
    @Query("SELECT c FROM Customer c WHERE c.status = :status")
    List<Customer> findInStatus(@Param("status") String status);
}
