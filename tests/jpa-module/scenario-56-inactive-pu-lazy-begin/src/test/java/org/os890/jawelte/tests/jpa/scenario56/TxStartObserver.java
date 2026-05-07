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
package org.os890.jawelte.tests.jpa.scenario56;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.module.jpa.api.event.TransactionStarted;

/** Records the persistence-unit name of every {@link TransactionStarted} fired. */
@ApplicationScoped
public class TxStartObserver {

    private final List<String> startedPersistenceUnits = Collections.synchronizedList(new ArrayList<>());

    /** Default constructor required by CDI. */
    public TxStartObserver() {
    }

    /**
     * Append the event's PU name to the recorded list.
     *
     * @param event the transaction-started event
     */
    public void on(@Observes TransactionStarted event) {
        startedPersistenceUnits.add(event.getPersistenceUnitName());
    }

    /**
     * Snapshot of the recorded PU names, in fire order.
     *
     * @return an unmodifiable copy
     */
    public List<String> startedPersistenceUnits() {
        synchronized (startedPersistenceUnits) {
            return List.copyOf(startedPersistenceUnits);
        }
    }
}
