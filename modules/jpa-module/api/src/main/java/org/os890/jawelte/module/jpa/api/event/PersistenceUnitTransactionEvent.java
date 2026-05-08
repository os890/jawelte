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
 * Common abstract base for the four persistence-unit-scoped transaction
 * events fired by jpa-module ({@link TransactionStarted},
 * {@link TransactionBeforeCompletion}, {@link TransactionCommitted},
 * {@link TransactionRolledBack}). Each event carries exactly the same
 * payload — the persistence-unit name on whose
 * {@code EntityTransaction} the event fired — so the base holds the
 * field, the constructor and the getter; the four concrete classes only
 * exist so consumers can route observers by event kind via CDI's typed
 * {@code @Observes} dispatch.
 *
 * <p>CDI observer resolution selects observers by the event's runtime
 * type, walking the type hierarchy as needed: a typed observer
 * {@code @Observes TransactionStarted} keeps working unchanged, while
 * a generic observer {@code @Observes PersistenceUnitTransactionEvent}
 * receives every event kind — useful for cross-cutting telemetry where
 * "anything happened on a tx" is the actual interest.
 */
public abstract class PersistenceUnitTransactionEvent {

    private final String persistenceUnitName;

    /**
     * Construct the event with the persistence unit it pertains to.
     *
     * @param persistenceUnitName the persistence unit name
     */
    protected PersistenceUnitTransactionEvent(String persistenceUnitName) {
        this.persistenceUnitName = persistenceUnitName;
    }

    /**
     * Get the persistence unit whose transaction this event describes.
     *
     * @return the persistence unit name
     */
    public String getPersistenceUnitName() {
        return persistenceUnitName;
    }
}
