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
package org.os890.jawelte.module.jpa.api.event;

/**
 * CDI event fired by jpa-module after a transaction rolls back —
 * either explicitly, on an unchecked exception escaping a
 * {@code @Transactional} method, or because of
 * {@code setRollbackOnly()}. Observers receive the persistence unit
 * name; a failing observer is caught and added to the
 * suppressed-exception chain on the rollback outcome.
 */
public class TransactionRolledBack {

    private final String persistenceUnitName;

    /**
     * Construct a {@code TransactionRolledBack} event.
     *
     * @param persistenceUnitName the persistence unit whose
     *                            transaction has just rolled back
     */
    public TransactionRolledBack(String persistenceUnitName) {
        this.persistenceUnitName = persistenceUnitName;
    }

    /**
     * Get the persistence unit whose transaction has just rolled
     * back.
     *
     * @return the persistence unit name
     */
    public String getPersistenceUnitName() {
        return persistenceUnitName;
    }
}
