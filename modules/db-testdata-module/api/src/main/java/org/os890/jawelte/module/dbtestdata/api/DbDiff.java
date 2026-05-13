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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.ServicePriorityResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.dbtestdata.api.DbDifference.DifferenceType;
import org.os890.jawelte.module.dbtestdata.api.port.DbDiffEngine;
import org.os890.jawelte.module.dbtestdata.api.port.ELInterpolator;
import org.os890.jawelte.module.dbtestdata.api.port.ELInterpolator.Context;
import org.os890.jawelte.module.dbtestdata.api.port.ELInterpolator.Context.FunctionDescriptor;
import org.os890.jawelte.module.jpa.api.JpaConfiguredPersistenceUnit;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.jpa.api.port.PersistenceUnitConnectionResolver;

/**
 * Static entry point for database verification. Mirrors
 * {@link DbSeed} on the read side: four factories return a
 * single-use {@link Builder}.
 *
 * <ul>
 *   <li>{@link #forConnection(Connection)} uses the supplied JDBC
 *       connection directly.</li>
 *   <li>{@link #forPersistenceUnit()} reads
 *       {@link PersistenceConfig#persistenceUnitName()} on the
 *       active test class; non-empty &rarr; named PU, empty / no
 *       annotation &rarr; delegate to
 *       {@link #forCurrentPersistenceUnit()}.</li>
 *   <li>{@link #forCurrentPersistenceUnit()} resolves the single
 *       persistence unit currently active on the calling thread.</li>
 *   <li>{@link #forPersistenceUnit(String)} resolves the named PU.</li>
 * </ul>
 */
public abstract class DbDiff {

    /** Utility class; not meant to be instantiated. */
    private DbDiff() {
    }

    /**
     * Use {@code connection} directly. The api never closes,
     * commits, or rolls back the connection.
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
     * non-empty its value names the PU; when empty &mdash; including
     * the path where jpa-module is not on the classpath at all
     * &mdash; this method delegates to
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
     * persistence unit on the calling thread.
     *
     * @return a fresh {@link Builder}
     */
    public static Builder forCurrentPersistenceUnit() {
        return new Builder(() -> resolver().connectionForActivePersistenceUnit());
    }

    /**
     * Resolve the connection of the named persistence unit.
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

    /** Default dataset format identifier when {@link Builder#format(String)} is not called. */
    private static final String DEFAULT_FORMAT = "dbunit-xml";

    private static final ConcurrentMap<String, DbDiffEngine> CACHED_DIFF_ENGINES = new ConcurrentHashMap<>();

    private static volatile ELInterpolator cachedInterpolator;

    private static String loadClasspathResource(String classpathResource) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(classpathResource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource not found: " + classpathResource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            throw new IllegalArgumentException(
                    "Failed to read classpath resource: " + classpathResource, ioException);
        }
    }

    private static DbDiffEngine resolveDiffEngine(String format) {
        DbDiffEngine cached = CACHED_DIFF_ENGINES.get(format);
        if (cached != null) {
            return cached;
        }
        List<DbDiffEngine> matching = new ArrayList<>();
        for (DbDiffEngine candidate : ServiceLoader.load(DbDiffEngine.class)) {
            if (format.equals(candidate.format())) {
                matching.add(candidate);
            }
        }
        if (matching.isEmpty()) {
            throw new IllegalArgumentException("Unknown dataset format: " + format);
        }
        ServicePriorityResolver resolver = TestContext.loadService(ServicePriorityResolver.class);
        DbDiffEngine resolved = resolver.resolve(matching);
        CACHED_DIFF_ENGINES.put(format, resolved);
        return resolved;
    }

    private static ELInterpolator resolveInterpolator() {
        ELInterpolator local = cachedInterpolator;
        if (local != null) {
            return local;
        }
        synchronized (DbDiff.class) {
            local = cachedInterpolator;
            if (local != null) {
                return local;
            }
            List<ELInterpolator> matching = new ArrayList<>();
            for (ELInterpolator candidate : ServiceLoader.load(ELInterpolator.class)) {
                matching.add(candidate);
            }
            if (matching.isEmpty()) {
                throw new IllegalStateException(
                        "No ELInterpolator registered — was db-testdata-module/impl included?");
            }
            ServicePriorityResolver resolver = TestContext.loadService(ServicePriorityResolver.class);
            local = resolver.resolve(matching);
            cachedInterpolator = local;
            return local;
        }
    }

    /**
     * Carrier for the options {@link Builder} hands to the active
     * {@link DbDiffEngine}. The six fields collapse the builder's
     * {@code ignoring(...)} / {@code subsetOnly()} /
     * {@code unorderedTables(...)} / boolean-extension lists plus
     * the EL {@link Context} into a single immutable
     * struct.
     *
     * @param ignorePatterns       column patterns to skip; values
     *                             follow the {@code *.COLUMN} (any
     *                             table) or {@code TABLE.COLUMN}
     *                             (specific) syntax
     * @param subsetOnly           {@code true} restricts the comparison
     *                             to the tables / columns present in
     *                             the expected dataset; {@code false}
     *                             reports extra rows and extra columns
     *                             as well
     * @param unorderedTables      uppercase table names whose rows are
     *                             compared as a multiset (row-order
     *                             insensitive)
     * @param booleanTrueValues    additional values, beyond the
     *                             built-in list ({@code true},
     *                             {@code 1}, {@code yes}, {@code y},
     *                             {@code on}, {@code t}), that
     *                             normalise to the boolean {@code true}
     *                             during cell comparison
     * @param booleanFalseValues   additional values, beyond the
     *                             built-in list ({@code false},
     *                             {@code 0}, {@code no}, {@code n},
     *                             {@code off}, {@code f}), that
     *                             normalise to the boolean {@code false}
     * @param interpolationContext bindings the diff engine forwards to
     *                             the active EL interpolator when it
     *                             evaluates {@code #{&hellip;}}
     *                             per-cell predicate markers
     */
    public record DiffSpec(
            List<String> ignorePatterns,
            boolean subsetOnly,
            List<String> unorderedTables,
            List<String> booleanTrueValues,
            List<String> booleanFalseValues,
            Context interpolationContext) {

        /**
         * Canonical constructor. Defensively copies every list so the
         * record remains immutable even if the caller mutates the
         * source after construction. {@code interpolationContext} is
         * already immutable (record).
         */
        public DiffSpec {
            ignorePatterns = List.copyOf(Objects.requireNonNull(ignorePatterns, "ignorePatterns"));
            unorderedTables = List.copyOf(Objects.requireNonNull(unorderedTables, "unorderedTables"));
            booleanTrueValues = List.copyOf(Objects.requireNonNull(booleanTrueValues, "booleanTrueValues"));
            booleanFalseValues = List.copyOf(Objects.requireNonNull(booleanFalseValues, "booleanFalseValues"));
            Objects.requireNonNull(interpolationContext, "interpolationContext");
        }
    }

    /**
     * Single-use fluent configuration returned by {@link DbDiff}'s
     * static factories. Accumulates the expected dataset source, the
     * format identifier, the ignore patterns, the subset / unordered
     * options, and the EL values / beans / function descriptors, then
     * runs the comparison via {@link #assertEquals()} or the targeted
     * row-count check via {@link #assertRowCount(String, int)}.
     *
     * <p>Builders are not thread-safe and must not be reused after a
     * terminal call.
     */
    public static class Builder {

        /** Underscore variant ({@code .} -> {@code _}) is resolved as a fallback by MP Config consumers. */
        static final String IGNORE_CONFIG_KEY = "org.os890.jawelte.module.dbtestdata.api.DbDiff.ignore";

        static final String UNORDERED_CONFIG_KEY = "org.os890.jawelte.module.dbtestdata.api.DbDiff.unordered-tables";

        static final String BOOLEAN_TRUE_CONFIG_KEY = "org.os890.jawelte.module.dbtestdata.api.DbDiff.boolean-true";

        static final String BOOLEAN_FALSE_CONFIG_KEY = "org.os890.jawelte.module.dbtestdata.api.DbDiff.boolean-false";

        private final Supplier<Connection> connectionSupplier;

        private String classpathResource;

        private String inlineContent;

        private String format = DEFAULT_FORMAT;

        private final List<String> ignorePatterns = new ArrayList<>();

        private boolean subsetOnly;

        private final List<String> unorderedTables = new ArrayList<>();

        private final Map<String, Object> values = new LinkedHashMap<>();

        private final Map<String, Object> beans = new LinkedHashMap<>();

        private final List<FunctionDescriptor> functions = new ArrayList<>();

        /** Constructor visible to {@link DbDiff} only. */
        Builder(Supplier<Connection> connectionSupplier) {
            this.connectionSupplier = connectionSupplier;
        }

        /**
         * Load the expected dataset from {@code classpathResource} via
         * the thread context classloader. Mutually exclusive with
         * {@link #expectedContent(String)}.
         *
         * @param classpathResource the classpath-relative path
         * @return this builder for chaining
         * @throws IllegalStateException if {@link #expectedContent(String)}
         *         has already been called
         */
        public Builder expected(String classpathResource) {
            if (inlineContent != null) {
                throw new IllegalStateException(
                        "expected(...) and expectedContent(...) are mutually exclusive");
            }
            this.classpathResource = Objects.requireNonNull(classpathResource, "classpathResource");
            return this;
        }

        /**
         * Use {@code content} as the expected dataset directly. Mutually
         * exclusive with {@link #expected(String)}.
         *
         * @param content the dataset text
         * @return this builder for chaining
         * @throws IllegalStateException if {@link #expected(String)} has
         *         already been called
         */
        public Builder expectedContent(String content) {
            if (classpathResource != null) {
                throw new IllegalStateException(
                        "expected(...) and expectedContent(...) are mutually exclusive");
            }
            this.inlineContent = Objects.requireNonNull(content, "content");
            return this;
        }

        /**
         * Pick the dataset format identifier. Defaults to
         * {@code "dbunit-xml"}.
         *
         * @param format the format identifier
         * @return this builder for chaining
         */
        public Builder format(String format) {
            this.format = Objects.requireNonNull(format, "format");
            return this;
        }

        /**
         * Add column-ignore patterns. {@code *.COLUMN} matches the named
         * column in every table; {@code TABLE.COLUMN} matches the
         * specific (table, column) pair. Cumulative across calls.
         *
         * @param patterns one or more ignore patterns
         * @return this builder for chaining
         */
        public Builder ignoring(String... patterns) {
            this.ignorePatterns.addAll(Arrays.asList(patterns));
            return this;
        }

        /**
         * Compare only the tables and columns present in the expected
         * dataset. Tables / columns / rows present in the database but
         * absent from the expected dataset are not reported as
         * differences.
         *
         * @return this builder for chaining
         */
        public Builder subsetOnly() {
            this.subsetOnly = true;
            return this;
        }

        /**
         * Compare the named tables row-as-multiset (no row-order
         * requirement). Names are case-insensitive at engine time; the
         * builder records them as supplied. Cumulative across calls.
         *
         * @param tableNames one or more table names
         * @return this builder for chaining
         */
        public Builder unorderedTables(String... tableNames) {
            this.unorderedTables.addAll(Arrays.asList(tableNames));
            return this;
        }

        /**
         * Add EL value bindings. Cumulative across calls.
         *
         * @param values map of EL bindings
         * @return this builder for chaining
         */
        public Builder withValues(Map<String, Object> values) {
            this.values.putAll(Objects.requireNonNull(values, "values"));
            return this;
        }

        /**
         * Register a named bean for EL. {@code ${name.method()}} resolves
         * the registered instance and invokes its method. Beans take
         * precedence over flat {@link #withValues(Map)} bindings when a
         * name collides.
         *
         * @param name     the EL-visible name
         * @param instance the bean instance; cannot be {@code null}
         * @return this builder for chaining
         */
        public Builder withBean(String name, Object instance) {
            this.beans.put(
                    Objects.requireNonNull(name, "name"),
                    Objects.requireNonNull(instance, "instance"));
            return this;
        }

        /**
         * Register a Jakarta EL function. The {@code declaringClass} must
         * host a {@code public static} method matching {@code methodName};
         * validation runs at registration time and an unknown method name
         * or a non-{@code public static} method raises
         * {@link IllegalArgumentException} from this call, not from
         * {@link #assertEquals()} later.
         *
         * @param prefix         the function prefix, e.g. {@code "fn"}
         * @param name           the function name, e.g. {@code "now"}
         * @param declaringClass the class hosting the static method
         * @param methodName     the static-method name
         * @return this builder for chaining
         * @throws IllegalArgumentException when {@code declaringClass}
         *                                  declares no method named
         *                                  {@code methodName} or when that
         *                                  method is not
         *                                  {@code public static}
         */
        public Builder withFunction(
                String prefix, String name, Class<?> declaringClass, String methodName) {
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(declaringClass, "declaringClass");
            Objects.requireNonNull(methodName, "methodName");
            validatePublicStaticMethod(declaringClass, methodName, prefix, name);
            this.functions.add(new FunctionDescriptor(prefix, name, declaringClass, methodName));
            return this;
        }

        private static void validatePublicStaticMethod(
                Class<?> declaringClass, String methodName, String prefix, String name) {
            Method matchedByName = null;
            for (Method candidate : declaringClass.getDeclaredMethods()) {
                if (!candidate.getName().equals(methodName)) {
                    continue;
                }
                matchedByName = candidate;
                if (Modifier.isStatic(candidate.getModifiers())
                        && Modifier.isPublic(candidate.getModifiers())) {
                    return;
                }
            }
            String label = "EL function " + prefix + ":" + name
                    + " — method '" + methodName + "' on " + declaringClass.getName();
            if (matchedByName == null) {
                throw new IllegalArgumentException(label + " — no public static method found");
            }
            throw new IllegalArgumentException(label + " — must be public static");
        }

        /**
         * Resolve the engine, run the diff, and throw
         * {@link AssertionError} when any difference is reported. Returns
         * silently on full match.
         *
         * @throws AssertionError           when at least one difference is
         *                                  reported
         * @throws IllegalStateException    if neither {@link #expected(String)}
         *                                  nor {@link #expectedContent(String)}
         *                                  was called
         * @throws IllegalArgumentException for an unknown format or a
         *                                  missing classpath resource
         */
        public void assertEquals() {
            String content = loadContent();
            ELInterpolator interpolator = resolveInterpolator();
            Context context = new Context(values, beans, functions);
            String interpolated = interpolator.interpolate(content, context);
            DbDiffEngine engine = resolveDiffEngine(format);
            DiffSpec spec = buildSpec(context);
            Connection connection = connectionSupplier.get();
            List<DbDifference> differences;
            try {
                differences = engine.diff(connection, interpolated, spec);
            } catch (RuntimeException diffFailure) {
                throw new RuntimeException(
                        "[DbDiff] Failed to read database: " + diffFailure.getMessage(), diffFailure);
            }
            if (differences.isEmpty()) {
                return;
            }
            throw new AssertionError(formatMessage(differences));
        }

        /**
         * Issue {@code SELECT COUNT(*) FROM tableName} on the resolved
         * connection and compare with {@code expectedCount}.
         *
         * @param tableName     the table to count rows in
         * @param expectedCount the expected row count
         * @throws AssertionError on count mismatch — message format is
         *                        {@code "Expected " + expected + " rows
         *                        in " + table + " but found " + actual}
         * @throws RuntimeException wrapping any {@link SQLException}
         */
        public void assertRowCount(String tableName, int expectedCount) {
            Objects.requireNonNull(tableName, "tableName");
            Connection connection = connectionSupplier.get();
            int actualCount;
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                if (!resultSet.next()) {
                    throw new RuntimeException(
                            "[DbDiff] Failed to read database: empty result for "
                                    + "SELECT COUNT(*) FROM " + tableName);
                }
                actualCount = resultSet.getInt(1);
            } catch (SQLException sqlException) {
                throw new RuntimeException(
                        "[DbDiff] Failed to read database: " + sqlException.getMessage(), sqlException);
            }
            if (actualCount != expectedCount) {
                throw new AssertionError(
                        "Expected " + expectedCount + " rows in " + tableName
                                + " but found " + actualCount);
            }
        }

        private String loadContent() {
            if (inlineContent != null) {
                return inlineContent;
            }
            if (classpathResource == null) {
                throw new IllegalStateException(
                        "Neither expected(...) nor expectedContent(...) was called");
            }
            return loadClasspathResource(classpathResource);
        }

        private DiffSpec buildSpec(Context interpolationContext) {
            List<String> mergedIgnore = mergeWithCsvDefaults(ignorePatterns, IGNORE_CONFIG_KEY);
            List<String> mergedUnordered = mergeWithCsvDefaults(unorderedTables, UNORDERED_CONFIG_KEY);
            List<String> trueExtras = csvDefaults(BOOLEAN_TRUE_CONFIG_KEY);
            List<String> falseExtras = csvDefaults(BOOLEAN_FALSE_CONFIG_KEY);
            return new DiffSpec(
                    mergedIgnore, subsetOnly, mergedUnordered, trueExtras, falseExtras, interpolationContext);
        }

        private static List<String> mergeWithCsvDefaults(List<String> base, String configKey) {
            List<String> fromConfig = csvDefaults(configKey);
            if (fromConfig.isEmpty()) {
                return List.copyOf(base);
            }
            List<String> merged = new ArrayList<>(base.size() + fromConfig.size());
            merged.addAll(fromConfig);
            merged.addAll(base);
            return List.copyOf(merged);
        }

        private static List<String> csvDefaults(String configKey) {
            ConfigResolver configResolver = TestContext.loadService(ConfigResolver.class);
            return configResolver.resolve(configKey)
                    .map(value -> Arrays.stream(value.split(","))
                            .map(String::trim)
                            .filter(entry -> !entry.isEmpty())
                            .toList())
                    .orElse(List.of());
        }

        private static String formatMessage(List<DbDifference> differences) {
            StringBuilder message = new StringBuilder();
            message.append("DB diff found ")
                    .append(differences.size())
                    .append(" difference(s):");
            for (DbDifference difference : differences) {
                message.append(System.lineSeparator()).append("  ");
                appendDifferenceLine(message, difference);
            }
            return message.toString();
        }

        private static void appendDifferenceLine(StringBuilder message, DbDifference difference) {
            message.append(difference.tableName())
                    .append('[')
                    .append(difference.rowIndex())
                    .append(']');
            if (difference.kind() == DifferenceType.VALUE_MISMATCH) {
                message.append('.')
                        .append(difference.columnName())
                        .append(": expected=\"")
                        .append(difference.expected())
                        .append("\" actual=\"")
                        .append(difference.actual())
                        .append('"');
            } else if (difference.kind() == DifferenceType.MISSING_ROW) {
                message.append(": missing row in database");
            } else {
                message.append(": unexpected row in database");
            }
            if (difference.expectedLineNumber() > 0) {
                message.append(" (expected file line ")
                        .append(difference.expectedLineNumber())
                        .append(')');
            }
        }
    }
}
