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
package example.txevents;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.module.jpa.api.event.TransactionCommitted;
import org.os890.jawelte.module.jpa.api.event.TransactionRolledBack;
import org.os890.jawelte.module.jpa.api.event.TransactionStarted;

/**
 * CDI bean that observes jpa-module's transaction events to build a
 * timeline. Useful for audit / metrics / cross-cutting hooks that
 * don't want to wrap every @Transactional service call with their
 * own boilerplate.
 */
@ApplicationScoped
public class TransactionAuditLog {

    private final List<String> timeline = new CopyOnWriteArrayList<>();

    public List<String> timeline() {
        return timeline;
    }

    void onStart(@Observes TransactionStarted event) {
        timeline.add("start:" + event.getPersistenceUnitName());
    }

    void onCommit(@Observes TransactionCommitted event) {
        timeline.add("commit:" + event.getPersistenceUnitName());
    }

    void onRollback(@Observes TransactionRolledBack event) {
        timeline.add("rollback:" + event.getPersistenceUnitName());
    }
}
