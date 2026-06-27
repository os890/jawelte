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
package org.os890.jawelte.tests.jpa.scenario67;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManagerFactory;

import org.os890.jawelte.module.jpa.api.port.TableNameResolver;
import org.os890.jawelte.module.jpa.impl.adapter.cleanup.InformationSchemaTableNameResolver;

/**
 * Test-only resolver that returns the real schema tables (delegating to
 * the default {@link InformationSchemaTableNameResolver}) plus one
 * non-existent table name. The bogus name makes the native cleanup's
 * fast path (drop FKs / DELETE rows / re-add FKs) fail deterministically
 * on H2 — the {@code DELETE} against the missing table throws.
 *
 * <p>That failure is the trigger this scenario needs: it forces
 * {@code NativeSqlDeleteDbCleanupStrategy} down its rollback +
 * schema-recreate fallback, the same recovery path the anonymous /
 * un-droppable foreign-key case takes. Forcing a missing table is the
 * cheapest failure that is reproducible on H2 (which auto-names every
 * foreign key, so the anonymous-FK trigger cannot occur there).
 *
 * <p>{@code @Priority(50)} — lower number wins the SPI sort over the
 * default resolver ({@code @Priority MAX_VALUE}).
 */
@Priority(50)
public class TestScenarioFailingTableNameResolver implements TableNameResolver {

    private final TableNameResolver delegate = new InformationSchemaTableNameResolver();

    /** No-arg constructor required by ServiceLoader. */
    public TestScenarioFailingTableNameResolver() {
    }

    @Override
    public List<String> resolveTableNames(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        List<String> tableNames =
                new ArrayList<>(delegate.resolveTableNames(persistenceUnitName, entityManagerFactory));
        tableNames.add("DOES_NOT_EXIST_TABLE");
        return tableNames;
    }
}
