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
 * CDI event fired by jpa-module after
 * {@code EntityTransaction.begin()} returns successfully. Observers
 * receive the persistence unit name the transaction was started for;
 * a failing observer is caught and added to the suppressed-exception
 * chain on the eventual commit/rollback outcome.
 */
public class TransactionStarted extends PersistenceUnitTransactionEvent {

    /**
     * Construct a {@code TransactionStarted} event.
     *
     * @param persistenceUnitName the persistence unit whose
     *                            transaction has just started
     */
    public TransactionStarted(String persistenceUnitName) {
        super(persistenceUnitName);
    }
}
