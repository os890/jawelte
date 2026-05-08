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
package org.os890.jawelte.tests.jpa.scenario38;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.module.jpa.api.event.TransactionBeforeCompletion;
import org.os890.jawelte.module.jpa.api.event.TransactionCommitted;
import org.os890.jawelte.module.jpa.api.event.TransactionStarted;

/** Records the order of jpa-module CDI tx events seen during the test. */
@ApplicationScoped
public class CommitEventRecorder {

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());

    /** Default constructor required by CDI. */
    public CommitEventRecorder() {
    }

    /** Record a {@link TransactionStarted} fire. */
    public void onStarted(@Observes TransactionStarted event) {
        events.add("started:" + event.getPersistenceUnitName());
    }

    /** Record a {@link TransactionBeforeCompletion} fire. */
    public void onBeforeCompletion(@Observes TransactionBeforeCompletion event) {
        events.add("before:" + event.getPersistenceUnitName());
    }

    /** Record a {@link TransactionCommitted} fire. */
    public void onCommitted(@Observes TransactionCommitted event) {
        events.add("committed:" + event.getPersistenceUnitName());
    }

    /** @return the recorded event sequence (in fire order) */
    public List<String> events() {
        return List.copyOf(events);
    }

    /** Clear the recorded sequence between tests. */
    public void reset() {
        events.clear();
    }
}
