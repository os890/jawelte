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
package org.os890.jawelte.module.jpa.impl.adapter.cleanup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;

/**
 * Table names that per-method cleanup must leave alone.
 *
 * <p>Cleanup deliberately works from the schema's actual tables rather
 * than from the JPA metamodel, so it reaches join tables, audit logs
 * and sequence bookkeeping that no {@code @Entity} describes. The same
 * reach also puts a schema-migration tool's history table in scope —
 * and those rows are not test data: they are the record of what has
 * already been applied, and emptying them while leaving the DDL they
 * describe puts the next migration run in front of a schema no history
 * remembers.
 *
 * <p>The failure that produces is unusually indirect: the class passes
 * in isolation and fails in a suite, because the first container is
 * always fine and only a later one meets the orphaned schema. Nothing
 * in the message points at cleanup.
 *
 * <p><b>Who supplies the names.</b> This class owns the logical key and
 * knows no tool. {@code db-migration-module} contributes the well-known
 * ones ({@code flyway_schema_history}, {@code DATABASECHANGELOG}, …)
 * through a {@link org.os890.jawelte.core.api.port.ConfigKeyAliasProvider},
 * and the merge is additive — a consumer's own value in
 * {@value #EXCLUDE_TABLES_KEY} is unioned with every contributor's
 * rather than replacing it. jpa-module therefore stays free of
 * third-party tool knowledge, and a deployment without that module
 * cleans exactly what it always did.
 *
 * <p>Matching is case-insensitive because the tools disagree: Flyway
 * lowercases its table, Liquibase uppercases its two.
 */
public abstract class CleanupTableExclusions {

    /**
     * MicroProfile Config key listing table names to keep out of
     * per-method cleanup, comma-separated and matched
     * case-insensitively.
     *
     * <p>The user's own extension channel for the concept: its value is
     * merged with contributor values, not substituted for them. To drop
     * a contributor default, override that contributor's own key in a
     * higher-ordinal source.
     */
    public static final String EXCLUDE_TABLES_KEY =
            "org.os890.jawelte.module.jpa.cleanup.exclude-tables";

    /** Suppress instantiation; the class is a static-method holder. */
    protected CleanupTableExclusions() {
    }

    /**
     * Drop excluded names from a resolved cleanup target list.
     *
     * @param tableNames the tables the active
     *                   {@link org.os890.jawelte.module.jpa.api.port.TableNameResolver}
     *                   returned
     * @return the tables cleanup should actually empty, in the order
     *         they were resolved; the same list when nothing is
     *         excluded
     */
    public static List<String> apply(List<String> tableNames) {
        Set<String> excluded = excludedTableNames();
        if (excluded.isEmpty() || tableNames.isEmpty()) {
            return tableNames;
        }
        List<String> retained = new ArrayList<>(tableNames.size());
        for (String tableName : tableNames) {
            if (!excluded.contains(tableName.toLowerCase(Locale.ROOT))) {
                retained.add(tableName);
            }
        }
        return retained;
    }

    /**
     * The configured exclusions, lowercased for matching.
     *
     * @return the excluded names; empty when the key is unset, which is
     *         the case for a deployment that ships no contributor
     */
    static Set<String> excludedTableNames() {
        ConfigResolver resolver = TestContext.loadService(ConfigResolver.class);
        Set<String> names = new LinkedHashSet<>();
        // Contributor keys first, then the owner key. The merge is the
        // owner's job: ConfigResolver.resolve(...) reads one key, and
        // resolveAliasKeysFor(...) is what names the contributors.
        for (String aliasKey : resolver.resolveAliasKeysFor(EXCLUDE_TABLES_KEY)) {
            appendLowercased(resolver, aliasKey, names);
        }
        appendLowercased(resolver, EXCLUDE_TABLES_KEY, names);
        return names;
    }

    private static void appendLowercased(ConfigResolver resolver, String key, Set<String> sink) {
        resolver.resolve(key).ifPresent(value -> {
            for (String entry : value.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    sink.add(trimmed.toLowerCase(Locale.ROOT));
                }
            }
        });
    }
}
