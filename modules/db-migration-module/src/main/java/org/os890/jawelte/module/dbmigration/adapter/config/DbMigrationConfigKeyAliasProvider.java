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
package org.os890.jawelte.module.dbmigration.adapter.config;

import java.util.List;

import org.os890.jawelte.core.api.port.ConfigKeyAliasProvider;

/**
 * Contributes this module's migration-bookkeeping table names to
 * jpa-module's cleanup exclusion list.
 *
 * <p>The merge jpa-module performs is additive, so a consumer setting
 * the owner key to name a home-grown history table gets that
 * <em>plus</em> these, rather than replacing them. Dropping one of
 * these defaults is done by overriding this module's own key
 * ({@value #MIGRATION_EXCLUDE_TABLES_KEY}) in a higher-ordinal
 * MicroProfile Config source.
 *
 * <p>The logical key is the literal string jpa-module publishes as
 * {@code CleanupTableExclusions.EXCLUDE_TABLES_KEY}; the constant is
 * not imported because jpa-module/impl is not API, and this module
 * deliberately has no dependency on jpa-module at all.
 *
 * <p>Discovered by {@code ConfigResolverAdapter} in core/impl via
 * {@link java.util.ServiceLoader}.
 */
public class DbMigrationConfigKeyAliasProvider implements ConfigKeyAliasProvider {

    private static final String CLEANUP_EXCLUDE_TABLES_LOGICAL_KEY =
            "org.os890.jawelte.module.jpa.cleanup.exclude-tables";

    /**
     * This module's own MP Config key, under which the well-known
     * table names ship in its
     * {@code META-INF/microprofile-config.properties}.
     */
    public static final String MIGRATION_EXCLUDE_TABLES_KEY =
            "org.os890.jawelte.module.dbmigration.cleanup.exclude-tables";

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public DbMigrationConfigKeyAliasProvider() {
    }

    @Override
    public List<String> aliasesFor(String logicalKey) {
        if (CLEANUP_EXCLUDE_TABLES_LOGICAL_KEY.equals(logicalKey)) {
            return List.of(MIGRATION_EXCLUDE_TABLES_KEY);
        }
        return List.of();
    }
}
