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
package org.os890.jawelte.module.dbtestdata.api;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.dbtestdata.api.SeedSpec.SeedMode;
import org.os890.jawelte.module.dbtestdata.api.port.DbSeedEngine;
import org.os890.jawelte.module.dbtestdata.api.port.ELInterpolator;
import org.os890.jawelte.module.jpa.api.JpaConfiguredPersistenceUnit;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.jpa.api.port.PersistenceUnitConnectionResolver;

/**
 * Static entry point for database fixtures. Four factories return
 * a single-use {@link Builder}:
 *
 * <ul>
 *   <li>{@link #forConnection(Connection)} uses the supplied JDBC
 *       connection directly — the api never closes, commits, or
 *       rolls it back.</li>
 *   <li>{@link #forPersistenceUnit()} resolves the persistence unit
 *       by consulting {@link PersistenceConfig#persistenceUnitName()}
 *       on the active test class; when that attribute is non-empty
 *       its value is the named PU, otherwise this method delegates to
 *       {@link #forCurrentPersistenceUnit()}.</li>
 *   <li>{@link #forCurrentPersistenceUnit()} resolves the single
 *       persistence unit currently active on the calling thread via
 *       the project-wide {@link PersistenceUnitConnectionResolver};
 *       multiple active PUs raise {@link IllegalStateException}.</li>
 *   <li>{@link #forPersistenceUnit(String)} resolves the named PU.</li>
 * </ul>
 */
public abstract class DbSeed {

    /** Utility class; not meant to be instantiated. */
    private DbSeed() {
    }

    /**
     * Use {@code connection} directly. The api never closes,
     * commits, or rolls back the connection; the caller owns the
     * transaction lifecycle.
     *
     * @param connection a non-{@code null} JDBC connection
     * @return a fresh {@link Builder}
     * @throws NullPointerException if {@code connection} is {@code null}
     */
    public static Builder forConnection(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new Builder(() -> connection);
    }

    /**
     * Resolve the persistence-unit connection driven by the active
     * test class's {@link PersistenceConfig#persistenceUnitName()}.
     * The annotation value is read once during jpa-module's
     * {@code beforeAll} hook and stored in
     * {@link JpaConfiguredPersistenceUnit}; when the stored value is
     * non-empty its value names the PU (resolved via
     * {@link PersistenceUnitConnectionResolver#connectionFor(String)});
     * when empty &mdash; including the path where jpa-module is not
     * on the classpath at all &mdash; this method delegates to
     * {@link #forCurrentPersistenceUnit()}.
     *
     * @return a fresh {@link Builder}
     */
    public static Builder forPersistenceUnit() {
        String configuredName = JpaConfiguredPersistenceUnit.get();
        if (configuredName.isEmpty()) {
            return forCurrentPersistenceUnit();
        }
        return new Builder(() -> resolver().connectionFor(configuredName));
    }

    /**
     * Resolve the connection of the single currently active
     * persistence unit on the calling thread. The active
     * {@link PersistenceUnitConnectionResolver}'s
     * {@code connectionForActivePersistenceUnit()} provides the
     * connection — it raises {@link IllegalStateException} when
     * zero or more than one PU is active.
     *
     * @return a fresh {@link Builder}
     */
    public static Builder forCurrentPersistenceUnit() {
        return new Builder(() -> resolver().connectionForActivePersistenceUnit());
    }

    /**
     * Resolve the connection of the named persistence unit via the
     * active {@link PersistenceUnitConnectionResolver}.
     *
     * @param unitName the persistence unit name
     * @return a fresh {@link Builder}
     */
    public static Builder forPersistenceUnit(String unitName) {
        Objects.requireNonNull(unitName, "unitName");
        return new Builder(() -> resolver().connectionFor(unitName));
    }

    private static PersistenceUnitConnectionResolver resolver() {
        PersistenceUnitConnectionResolver resolver = TestContext.loadService(PersistenceUnitConnectionResolver.class);
        if (resolver == null) {
            throw new IllegalStateException(
                    "No PersistenceUnitConnectionResolver registered. Is jpa-module "
                            + "(or another resolver impl) on the classpath?");
        }
        return resolver;
    }

    /**
     * Single-use fluent configuration returned by {@link DbSeed}'s static
     * factories. Accumulates the dataset source, the SQL-shape mode,
     * the format identifier, and the EL value bindings, then executes
     * the seed via {@link #execute()}.
     *
     * <p>Builders are not thread-safe and must not be reused after
     * {@link #execute()} returns or throws.
     */
    public static class Builder {

        private final Supplier<Connection> connectionSupplier;

        private String classpathResource;

        private String inlineContent;

        private String format = DatasetSupport.DEFAULT_FORMAT;

        private SeedMode mode = SeedMode.CLEAN_INSERT;

        private final Map<String, Object> values = new LinkedHashMap<>();

        /** Constructor visible to {@link DbSeed} only. */
        Builder(Supplier<Connection> connectionSupplier) {
            this.connectionSupplier = connectionSupplier;
        }

        /**
         * Load the dataset content from {@code classpathResource} via
         * the thread context classloader. Mutually exclusive with
         * {@link #datasetContent(String)}.
         *
         * @param classpathResource the classpath-relative path
         * @return this builder for chaining
         * @throws IllegalStateException if {@link #datasetContent(String)}
         *         has already been called
         */
        public Builder dataset(String classpathResource) {
            if (inlineContent != null) {
                throw new IllegalStateException(
                        "dataset(...) and datasetContent(...) are mutually exclusive");
            }
            this.classpathResource = Objects.requireNonNull(classpathResource, "classpathResource");
            return this;
        }

        /**
         * Use {@code content} as the dataset directly. Mutually
         * exclusive with {@link #dataset(String)}.
         *
         * @param content the dataset text
         * @return this builder for chaining
         * @throws IllegalStateException if {@link #dataset(String)} has
         *         already been called
         */
        public Builder datasetContent(String content) {
            if (classpathResource != null) {
                throw new IllegalStateException(
                        "dataset(...) and datasetContent(...) are mutually exclusive");
            }
            this.inlineContent = Objects.requireNonNull(content, "content");
            return this;
        }

        /**
         * Pick the dataset format identifier. Defaults to
         * {@code "dbunit-xml"} when this method is not called. Unknown
         * formats raise {@link IllegalArgumentException} from
         * {@link #execute()}, not from here.
         *
         * @param format the format identifier
         * @return this builder for chaining
         */
        public Builder format(String format) {
            this.format = Objects.requireNonNull(format, "format");
            return this;
        }

        /**
         * Select {@link SeedMode#CLEAN_INSERT} — the default mode. DELETE
         * every row in the dataset's tables in reverse foreign-key order,
         * then INSERT.
         *
         * @return this builder for chaining
         */
        public Builder cleanInsert() {
            this.mode = SeedMode.CLEAN_INSERT;
            return this;
        }

        /**
         * Select {@link SeedMode#INSERT}. INSERT only; duplicate PK
         * propagates as {@link RuntimeException}.
         *
         * @return this builder for chaining
         */
        public Builder insert() {
            this.mode = SeedMode.INSERT;
            return this;
        }

        /**
         * Select {@link SeedMode#UPDATE}. UPDATE existing rows by PK;
         * missing rows propagate as {@link RuntimeException}.
         *
         * @return this builder for chaining
         */
        public Builder update() {
            this.mode = SeedMode.UPDATE;
            return this;
        }

        /**
         * Select {@link SeedMode#REFRESH}. Upsert (INSERT when absent,
         * UPDATE when present). Safe under circular foreign-key
         * dependencies.
         *
         * @return this builder for chaining
         */
        public Builder refresh() {
            this.mode = SeedMode.REFRESH;
            return this;
        }

        /**
         * Add EL value bindings. Cumulative — multiple calls union the
         * bindings (later keys override earlier ones).
         *
         * @param values map of EL bindings
         * @return this builder for chaining
         */
        public Builder withValues(Map<String, Object> values) {
            this.values.putAll(Objects.requireNonNull(values, "values"));
            return this;
        }

        /**
         * Resolve the connection, load and interpolate the dataset,
         * resolve the active {@link DbSeedEngine} for {@link #format(String)},
         * and call {@code engine.seed(connection, content, SeedSpec)}.
         * Wraps any engine-side failure in {@code RuntimeException}
         * prefixed with {@code "[DbSeed] Failed to seed dataset: "}.
         *
         * @throws IllegalStateException    if neither {@link #dataset(String)}
         *                                  nor {@link #datasetContent(String)}
         *                                  was called
         * @throws IllegalArgumentException for an unknown format or a
         *                                  missing classpath resource
         */
        public void execute() {
            String content = loadContent();
            ELInterpolator interpolator = DatasetSupport.resolveInterpolator();
            InterpolationContext context = new InterpolationContext(values, Map.of(), List.of());
            String interpolated = interpolator.interpolateAll(content, context);
            DbSeedEngine engine = DatasetSupport.resolveSeedEngine(format);
            Connection connection = connectionSupplier.get();
            try {
                engine.seed(connection, interpolated, new SeedSpec(mode));
            } catch (RuntimeException seedFailure) {
                throw new RuntimeException(
                        "[DbSeed] Failed to seed dataset: " + seedFailure.getMessage(), seedFailure);
            }
        }

        private String loadContent() {
            if (inlineContent != null) {
                return inlineContent;
            }
            if (classpathResource == null) {
                throw new IllegalStateException(
                        "Neither dataset(...) nor datasetContent(...) was called");
            }
            return DatasetSupport.loadClasspathResource(classpathResource);
        }
    }
}
