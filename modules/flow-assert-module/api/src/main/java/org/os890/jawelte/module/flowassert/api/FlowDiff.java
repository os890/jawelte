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
package org.os890.jawelte.module.flowassert.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;
import org.os890.jawelte.core.api.port.ServicePriorityResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.flowassert.api.port.FlowDialect;
import org.os890.jawelte.module.flowassert.api.port.FlowDiffEngine;

/**
 * Fluent entry point for comparing what a test method recorded
 * against an expected sequence-diagram — the flow analogue of
 * {@code ContentDiff} and {@code DbDiff}.
 *
 * <h2>What is compared</h2>
 *
 * <p>{@link #forRecordedFlows()} compares the <em>combined</em>
 * diagram of the test method: one block per outermost call, in the
 * order they happened, sharing the participant lanes. The three
 * single-chain factories compare one flow on its own.
 *
 * <h2>Which notation</h2>
 *
 * <p>The expected file decides. Its extension selects the
 * {@link FlowDialect} — {@code .mmd} is Mermaid, {@code .puml} is
 * PlantUML, anything else belongs to whichever dialect claims it —
 * and the recording is rendered in that same notation. So switching
 * an assertion from Mermaid to PlantUML is a rename of the expected
 * file and nothing else.
 *
 * <h2>What is ignored</h2>
 *
 * <p>Durations, timestamps, thread names and notation boilerplate are
 * rendered but not compared, because they differ from run to run;
 * {@link Builder#comparingTimings()} opts in. Hotspot markers are
 * ignored for the same reason. Everything structural — participants,
 * calls, returns, thrown types, events, loop counts, the number and
 * order of chains — is compared.
 *
 * <h2>Engine and dialect selection</h2>
 *
 * <p>Dialects are enumerated via {@link ServiceLoader}, filtered by
 * file extension respectively name, and resolved through the active
 * {@link ServicePriorityResolver}; the winner is cached per key for
 * the JVM lifetime. The single {@link FlowDiffEngine} is resolved
 * through {@link TestContext#loadService(Class)} once.
 *
 * <pre>{@code
 * FlowDiff.forRecordedFlows()
 *         .expected("flows/order-placement.mmd")
 *         .ignoringSubtree("AuditService#log(*)")
 *         .assertEquals();
 * }</pre>
 *
 * <p>{@code abstract} plus a private constructor per the project's
 * static-utility class convention.
 */
public abstract class FlowDiff {

    private static final ConcurrentMap<String, FlowDialect> CACHED_DIALECTS = new ConcurrentHashMap<>();

    private static volatile FlowDiffEngine cachedEngine;

    private FlowDiff() {
    }

    /**
     * Compare the combined diagram of everything the current test
     * method recorded.
     *
     * @return a fresh single-use {@link Builder}
     */
    public static Builder forRecordedFlows() {
        return new Builder(null, null, null);
    }

    /**
     * Compare the single chain whose outermost call went into
     * {@code beanClass}. The first matching flow wins when a bean was
     * the entry point more than once.
     *
     * @param beanClass the bean class expected to be the entry point;
     *                  must not be {@code null}
     * @return a fresh single-use {@link Builder}
     */
    public static Builder forEntryPoint(Class<?> beanClass) {
        Objects.requireNonNull(beanClass, "beanClass");
        return new Builder(null, beanClass, null);
    }

    /**
     * Compare the single chain whose outermost call went into
     * {@code beanClass#methodName}.
     *
     * @param beanClass  the bean class expected to be the entry point;
     *                   must not be {@code null}
     * @param methodName the method expected to be the entry point;
     *                   must not be {@code null}
     * @return a fresh single-use {@link Builder}
     */
    public static Builder forEntryPoint(Class<?> beanClass, String methodName) {
        Objects.requireNonNull(beanClass, "beanClass");
        Objects.requireNonNull(methodName, "methodName");
        return new Builder(null, beanClass, methodName);
    }

    /**
     * Compare one flow the caller already holds — from
     * {@link RecordedFlows} or from a {@code FlowSink} of its own.
     *
     * @param flow the flow to compare; must not be {@code null}
     * @return a fresh single-use {@link Builder}
     */
    public static Builder forFlow(CallFlow flow) {
        Objects.requireNonNull(flow, "flow");
        return new Builder(flow, null, null);
    }

    /**
     * Resolve the expected resource of a test method by convention:
     * {@code <baseDirectory>/<TestClassSimpleName>/<methodName><extension>},
     * probing the extensions of every registered dialect in priority
     * order. This is what {@link ExpectedFlow} with no value uses.
     *
     * @param baseDirectory the classpath directory to start from; must
     *                      not be {@code null}
     * @param testClass     the test class; must not be {@code null}
     * @param methodName    the test method name; must not be {@code null}
     * @return the first resource that exists, or
     *         {@link Optional#empty()} when none does
     */
    public static Optional<String> resolveExpectedResource(
            String baseDirectory, Class<?> testClass, String methodName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String candidate : expectedResourceCandidates(baseDirectory, testClass, methodName)) {
            if (classLoader.getResource(candidate) != null) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * The resources {@link #resolveExpectedResource(String, Class, String)}
     * probes, in the order it probes them — the list a failed
     * convention lookup reports.
     *
     * @param baseDirectory the classpath directory to start from; must
     *                      not be {@code null}
     * @param testClass     the test class; must not be {@code null}
     * @param methodName    the test method name; must not be {@code null}
     * @return the candidate resources; never {@code null}, never empty
     */
    public static List<String> expectedResourceCandidates(
            String baseDirectory, Class<?> testClass, String methodName) {
        String prefix = baseDirectory.isEmpty() ? "" : baseDirectory + "/";
        List<String> candidates = new ArrayList<>();
        for (String extension : knownFileExtensions()) {
            candidates.add(prefix + testClass.getSimpleName() + "/" + methodName + extension);
        }
        return List.copyOf(candidates);
    }

    static FlowDialect dialectByName(String name) {
        Objects.requireNonNull(name, "name");
        String key = "name:" + name.toLowerCase(Locale.ROOT);
        FlowDialect cached = CACHED_DIALECTS.get(key);
        if (cached != null) {
            return cached;
        }
        FlowDialect resolved = resolve(candidate -> candidate.name().equalsIgnoreCase(name),
                "No FlowDialect named '" + name + "'");
        CACHED_DIALECTS.put(key, resolved);
        return resolved;
    }

    static FlowDialect dialectByExtension(String resource) {
        String extension = extensionOf(resource);
        String key = "extension:" + extension;
        FlowDialect cached = CACHED_DIALECTS.get(key);
        if (cached != null) {
            return cached;
        }
        FlowDialect resolved = resolve(candidate -> candidate.fileExtensions().contains(extension),
                "No FlowDialect claims the extension '" + extension + "' of '" + resource
                        + "'. Registered: " + registeredDialects());
        CACHED_DIALECTS.put(key, resolved);
        return resolved;
    }

    static String extensionOf(String resource) {
        int lastDot = resource.lastIndexOf('.');
        if (lastDot < 0 || lastDot == resource.length() - 1) {
            throw new IllegalArgumentException("Expected resource '" + resource
                    + "' has no file extension - the extension is what selects the notation.");
        }
        return resource.substring(lastDot).toLowerCase(Locale.ROOT);
    }

    private static Set<String> knownFileExtensions() {
        Set<String> extensions = new LinkedHashSet<>();
        for (FlowDialect dialect : sortedDialects()) {
            extensions.addAll(dialect.fileExtensions());
        }
        return extensions;
    }

    private static String registeredDialects() {
        List<String> names = new ArrayList<>();
        for (FlowDialect dialect : sortedDialects()) {
            names.add(dialect.name() + " " + dialect.fileExtensions());
        }
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    private static List<FlowDialect> sortedDialects() {
        List<FlowDialect> candidates = new ArrayList<>();
        for (FlowDialect dialect : ServiceLoader.load(FlowDialect.class)) {
            candidates.add(dialect);
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        ServicePriorityResolver resolver = TestContext.loadService(ServicePriorityResolver.class);
        List<FlowDialect> sorted = new ArrayList<>(candidates);
        // resolve(...) returns the head of the priority-sorted list; sorting the
        // whole list means repeatedly resolving the remainder, which keeps the
        // project-wide priority order without duplicating the comparator here.
        List<FlowDialect> ordered = new ArrayList<>(candidates.size());
        while (!sorted.isEmpty()) {
            FlowDialect head = resolver.resolve(sorted);
            ordered.add(head);
            sorted.remove(head);
        }
        return List.copyOf(ordered);
    }

    private static FlowDialect resolve(DialectFilter filter, String failureMessage) {
        List<FlowDialect> matching = new ArrayList<>();
        for (FlowDialect dialect : sortedDialects()) {
            if (filter.matches(dialect)) {
                matching.add(dialect);
            }
        }
        if (matching.isEmpty()) {
            throw new IllegalStateException(failureMessage);
        }
        return matching.get(0);
    }

    private static FlowDiffEngine engine() {
        FlowDiffEngine cached = cachedEngine;
        if (cached != null) {
            return cached;
        }
        FlowDiffEngine resolved = TestContext.loadService(FlowDiffEngine.class);
        if (resolved == null) {
            throw new IllegalStateException("No " + FlowDiffEngine.class.getName()
                    + " on the classpath - add jawelte-flow-assert-module-impl to the test classpath.");
        }
        cachedEngine = resolved;
        return resolved;
    }

    /**
     * Filter over the registered dialects — a named type rather than a
     * {@code Predicate} so the api jar keeps its imports to what it
     * actually needs.
     */
    private interface DialectFilter {

        boolean matches(FlowDialect dialect);
    }

    /**
     * Immutable bag of options a {@link FlowDiffEngine} consumes. New
     * option fields are added here without breaking the engine port;
     * an engine that does not understand one treats it as a no-op.
     *
     * @param ignorePatterns        call patterns whose call and return
     *                              are dropped from both sides, in the
     *                              {@code Participant#signature} form
     *                              with {@code *} as the wildcard;
     *                              never {@code null}
     * @param ignoreSubtreePatterns call patterns whose call, return and
     *                              everything nested in between are
     *                              dropped from both sides; never
     *                              {@code null}
     * @param ignoreChainPatterns   entry-point patterns
     *                              ({@code Type.method}) whose whole
     *                              block is dropped from a combined
     *                              diagram; never {@code null}
     * @param compareTimings        whether durations, timestamps and
     *                              thread names participate
     * @param compareHotspots       whether hotspot markers participate
     * @param compareLoopCounts     whether the iteration count of a
     *                              folded loop participates
     * @param compareTitle          whether the diagram title
     *                              participates
     */
    public record DiffSpec(
            List<String> ignorePatterns,
            List<String> ignoreSubtreePatterns,
            List<String> ignoreChainPatterns,
            boolean compareTimings,
            boolean compareHotspots,
            boolean compareLoopCounts,
            boolean compareTitle) {

        /**
         * Defensively copies the three pattern lists so a caller
         * mutating the source afterwards cannot affect the record.
         *
         * @param ignorePatterns        call patterns to drop
         * @param ignoreSubtreePatterns call patterns to drop with their subtree
         * @param ignoreChainPatterns   entry-point patterns to drop
         * @param compareTimings        whether timings participate
         * @param compareHotspots       whether hotspot markers participate
         * @param compareLoopCounts     whether loop counts participate
         * @param compareTitle          whether the title participates
         */
        public DiffSpec {
            ignorePatterns = List.copyOf(ignorePatterns);
            ignoreSubtreePatterns = List.copyOf(ignoreSubtreePatterns);
            ignoreChainPatterns = List.copyOf(ignoreChainPatterns);
        }
    }

    /**
     * A single difference between the expected diagram and the
     * recording.
     *
     * @param kind               what kind of difference this is
     * @param expected           the expected step, described as
     *                           {@code From->To: label}, or
     *                           {@value #MISSING} when the expected
     *                           side has nothing here
     * @param actual             the recorded step in the same shape, or
     *                           {@value #MISSING}
     * @param expectedLineNumber 1-based line in the expected diagram,
     *                           or {@code 0} when the difference has no
     *                           place there
     * @param actualLineNumber   1-based line in the rendered recording,
     *                           or {@code 0}
     * @param chainIndex         0-based index of the block the
     *                           difference sits in
     * @param depth              call-nesting depth of the difference
     */
    public record Difference(
            Kind kind,
            String expected,
            String actual,
            int expectedLineNumber,
            int actualLineNumber,
            int chainIndex,
            int depth) {

        /**
         * Placeholder used in {@link #expected()} / {@link #actual()}
         * when that side has no step at this position at all — as
         * opposed to having a different one.
         */
        public static final String MISSING = "<missing>";

        /** The categories of difference the built-in engine reports. */
        public enum Kind {

            /** The recording contains a call the expected diagram does not. */
            UNEXPECTED_CALL,

            /** The expected diagram contains a call the recording does not. */
            MISSING_CALL,

            /** Same caller, same signature, a different callee. */
            DIFFERENT_TARGET,

            /** Same caller and callee, a different signature. */
            DIFFERENT_SIGNATURE,

            /** Same participants, a different returned or thrown type. */
            DIFFERENT_RETURN,

            /** Both sides have the step, at different positions. */
            WRONG_ORDER,

            /** A participant lane the recording never declared. */
            MISSING_PARTICIPANT,

            /** A participant lane the expected diagram does not declare. */
            UNEXPECTED_PARTICIPANT,

            /** The recording contains an outermost call the expected diagram does not. */
            UNEXPECTED_CHAIN,

            /** The expected diagram contains an outermost call the recording does not. */
            MISSING_CHAIN,

            /** A folded loop ran a different number of times. */
            LOOP_COUNT,

            /** A duration, timestamp or thread name differs - only reported when compared. */
            TIMING,

            /** A hotspot marker differs - only reported when compared. */
            HOTSPOT,

            /** The diagram title differs - only reported when compared. */
            TITLE
        }
    }

    /**
     * Collects what to compare and runs the comparison. Single-use and
     * not thread-safe: obtain one per assertion from a factory on
     * {@link FlowDiff}.
     */
    public static class Builder {

        private final CallFlow explicitFlow;
        private final Class<?> entryPointClass;
        private final String entryPointMethod;

        private final List<String> additionalIgnorePatterns = new ArrayList<>();
        private final List<String> ignoreSubtreePatterns = new ArrayList<>();
        private final List<String> ignoreChainPatterns = new ArrayList<>();

        private String expectedResource;
        private String expectedContent;
        private String expectedFormat;
        private boolean compareTimings;
        private boolean compareHotspots;
        private boolean compareTitle;

        Builder(CallFlow explicitFlow, Class<?> entryPointClass, String entryPointMethod) {
            this.explicitFlow = explicitFlow;
            this.entryPointClass = entryPointClass;
            this.entryPointMethod = entryPointMethod;
        }

        /**
         * Load the expected diagram from a classpath resource, using
         * the thread's context classloader. Its extension selects the
         * notation of the comparison. Mutually exclusive with
         * {@link #expectedContent(String, String)}.
         *
         * <p>The resource is read lazily during
         * {@link #assertEquals()}, so a missing one surfaces there.
         *
         * @param classpathResource the classpath-relative path; must
         *                          not be {@code null}
         * @return this builder for chaining
         * @throws IllegalStateException if
         *         {@link #expectedContent(String, String)} was already called
         */
        public Builder expected(String classpathResource) {
            Objects.requireNonNull(classpathResource, "classpathResource");
            if (expectedContent != null) {
                throw new IllegalStateException(
                        "expected(...) and expectedContent(...) are mutually exclusive");
            }
            this.expectedResource = classpathResource;
            return this;
        }

        /**
         * Use {@code content} as the expected diagram directly. With
         * no file extension to read the notation from, the notation is
         * named explicitly. Mutually exclusive with
         * {@link #expected(String)}.
         *
         * @param content the expected diagram text; must not be {@code null}
         * @param format  the notation name, e.g. {@code "mermaid"};
         *                must not be {@code null}
         * @return this builder for chaining
         * @throws IllegalStateException if {@link #expected(String)}
         *         was already called
         */
        public Builder expectedContent(String content, String format) {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(format, "format");
            if (expectedResource != null) {
                throw new IllegalStateException(
                        "expected(...) and expectedContent(...) are mutually exclusive");
            }
            this.expectedContent = content;
            this.expectedFormat = format;
            return this;
        }

        /**
         * Leave matching calls out of the comparison — the call itself
         * and its return, while everything it called stays. Cumulative
         * with the defaults from
         * {@value FlowAssertConfig#IGNORE_DEFAULTS_KEY}.
         *
         * <p>A pattern is {@code Participant#signature} with {@code *}
         * as the wildcard: {@code AuditService#log(*)},
         * {@code AuditService#*}, {@code *#log(String)}.
         *
         * @param patterns the patterns to add; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder ignoring(String... patterns) {
            additionalIgnorePatterns.addAll(List.of(patterns));
            return this;
        }

        /**
         * Leave matching calls and everything nested inside them out
         * of the comparison — for a collaborator whose internals a
         * test does not want to pin.
         *
         * @param patterns the patterns to add, same dialect as
         *                 {@link #ignoring(String...)}; must not be
         *                 {@code null}
         * @return this builder for chaining
         */
        public Builder ignoringSubtree(String... patterns) {
            ignoreSubtreePatterns.addAll(List.of(patterns));
            return this;
        }

        /**
         * Leave whole blocks out of a combined comparison, selected by
         * entry point ({@code Type.method}, {@code *} as the
         * wildcard) — the equivalent of the recorder's
         * {@code cdi-flow.combined-exclude-pattern}, per assertion.
         *
         * @param entryPointPatterns the patterns to add; must not be
         *                           {@code null}
         * @return this builder for chaining
         */
        public Builder ignoringChains(String... entryPointPatterns) {
            ignoreChainPatterns.addAll(List.of(entryPointPatterns));
            return this;
        }

        /**
         * Compare durations, timestamps and thread names as well.
         * Off by default, and only useful against an expected file
         * whose timings were normalised by hand.
         *
         * @return this builder for chaining
         */
        public Builder comparingTimings() {
            this.compareTimings = true;
            return this;
        }

        /**
         * Compare hotspot markers as well. Off by default: whether a
         * call exceeds {@code hotspotThresholdMillis} depends on the
         * machine the test runs on.
         *
         * @return this builder for chaining
         */
        public Builder comparingHotspots() {
            this.compareHotspots = true;
            return this;
        }

        /**
         * Compare the diagram title as well. Off by default, so a
         * {@code use-case.mmd} copied out of an application run — it
         * carries the use-case as its title — matches a recording that
         * has no title.
         *
         * @return this builder for chaining
         */
        public Builder comparingTitle() {
            this.compareTitle = true;
            return this;
        }

        /**
         * The recording, rendered in the notation the expected side is
         * written in. Useful for printing what happened while writing
         * a new expected file.
         *
         * @return the rendered recording; never {@code null}
         * @throws IllegalStateException if neither
         *         {@link #expected(String)} nor
         *         {@link #expectedContent(String, String)} was called
         */
        public String actualDiagram() {
            return renderActual(dialect());
        }

        /**
         * Run the comparison. Returns silently when the recording
         * matches; otherwise writes the recording next to the build
         * output and throws an {@link AssertionError} naming every
         * difference with its line in the expected file.
         *
         * @throws AssertionError           when the recording differs
         * @throws IllegalArgumentException if the expected resource is
         *                                  missing, or its extension
         *                                  belongs to no dialect
         * @throws IllegalStateException    if no expected side was
         *                                  named, or a single-chain
         *                                  assertion found no matching
         *                                  flow
         */
        public void assertEquals() {
            FlowDialect dialect = dialect();
            String actual = renderActual(dialect);
            String expected = resolveExpected(dialect, actual);
            DiffSpec spec = buildSpec();

            List<Difference> differences =
                    engine().diff(dialect.parse(expected), dialect.parse(actual), spec);
            if (differences.isEmpty()) {
                return;
            }
            Path writtenTo = writeActual(dialect, actual);
            throw new AssertionError(formatMessage(dialect, differences, actual, writtenTo));
        }

        private FlowDialect dialect() {
            if (expectedResource != null) {
                return dialectByExtension(expectedResource);
            }
            if (expectedContent != null) {
                return dialectByName(expectedFormat);
            }
            throw new IllegalStateException(
                    "Neither expected(...) nor expectedContent(...) was called");
        }

        private String renderActual(FlowDialect dialect) {
            if (explicitFlow != null) {
                return dialect.renderSingle(explicitFlow);
            }
            if (entryPointClass == null) {
                return dialect.render(RecordedFlows.all());
            }
            Optional<CallFlow> flow = entryPointMethod == null
                    ? RecordedFlows.byEntryPoint(entryPointClass)
                    : RecordedFlows.byEntryPoint(entryPointClass, entryPointMethod);
            return dialect.renderSingle(flow.orElseThrow(() -> new IllegalStateException(
                    "No recorded flow entered through " + entryPointClass.getName()
                            + (entryPointMethod == null ? "" : "#" + entryPointMethod)
                            + ". Recorded: " + recordedEntryPoints())));
        }

        private String recordedEntryPoints() {
            List<String> entryPoints = new ArrayList<>();
            for (CallFlow flow : RecordedFlows.all()) {
                entryPoints.add(flow.entryTypeSimpleName() + "." + flow.entryMethodName());
            }
            return entryPoints.isEmpty() ? "nothing" : String.join(", ", entryPoints);
        }

        private DiffSpec buildSpec() {
            List<String> ignorePatterns = new ArrayList<>(FlowAssertConfig.list(
                    FlowAssertConfig.IGNORE_DEFAULTS_KEY));
            ignorePatterns.addAll(additionalIgnorePatterns);
            boolean timings = compareTimings
                    || FlowAssertConfig.flag(FlowAssertConfig.COMPARE_TIMINGS_KEY, false);
            return new DiffSpec(ignorePatterns, ignoreSubtreePatterns, ignoreChainPatterns,
                    timings, compareHotspots, true, compareTitle);
        }

        private String resolveExpected(FlowDialect dialect, String actual) {
            if (expectedContent != null) {
                return expectedContent;
            }
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            try (InputStream stream = classLoader.getResourceAsStream(expectedResource)) {
                if (stream != null) {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException readFailure) {
                throw new IllegalArgumentException(
                        "Failed to read classpath resource: " + expectedResource, readFailure);
            }
            return onMissingExpectedResource(dialect, actual);
        }

        private String onMissingExpectedResource(FlowDialect dialect, String actual) {
            if (!FlowAssertConfig.flag(FlowAssertConfig.CREATE_MISSING_EXPECTED_KEY, false)) {
                throw new IllegalArgumentException("Expected resource not found: " + expectedResource
                        + ". Set " + FlowAssertConfig.CREATE_MISSING_EXPECTED_KEY
                        + "=true to have this run create it from the recording.");
            }
            Path created = Path.of(FlowAssertConfig.text(
                            FlowAssertConfig.CREATE_MISSING_EXPECTED_DIRECTORY_KEY, "src/test/resources"))
                    .resolve(expectedResource);
            write(created, actual);
            throw new AssertionError("Expected resource " + expectedResource
                    + " did not exist and was created from the recording (" + dialect.name() + "): "
                    + created.toAbsolutePath()
                    + System.lineSeparator() + System.lineSeparator() + actual
                    + System.lineSeparator()
                    + "Review it, then run again - an approval nobody looked at is not an assertion.");
        }

        private Path writeActual(FlowDialect dialect, String actual) {
            if (!FlowAssertConfig.flag(FlowAssertConfig.WRITE_ACTUAL_KEY, true)) {
                return null;
            }
            String directory = FlowAssertConfig.text(
                    FlowAssertConfig.ACTUAL_DIRECTORY_KEY, "target/flow-diagrams");
            Path target = Path.of(directory).resolve(actualFileName(dialect));
            return write(target, actual);
        }

        private String actualFileName(FlowDialect dialect) {
            String extension = expectedResource != null
                    ? extensionOf(expectedResource)
                    : dialect.fileExtensions().iterator().next();
            Class<?> testClass = RecordedFlows.testClass();
            String directory = testClass == null ? "" : testClass.getSimpleName() + "/";
            String methodName = RecordedFlows.testMethodName();
            String base = methodName == null ? describeSubject() : methodName;
            return directory + base + ".actual" + extension;
        }

        private String describeSubject() {
            if (explicitFlow != null) {
                return explicitFlow.entryTypeSimpleName() + "_" + explicitFlow.entryMethodName();
            }
            if (entryPointClass != null) {
                return entryPointClass.getSimpleName()
                        + (entryPointMethod == null ? "" : "_" + entryPointMethod);
            }
            if (expectedResource == null) {
                return "recorded-flow";
            }
            String fileName = expectedResource.substring(expectedResource.lastIndexOf('/') + 1);
            int lastDot = fileName.lastIndexOf('.');
            return lastDot < 0 ? fileName : fileName.substring(0, lastDot);
        }

        private static Path write(Path target, String content) {
            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(target, content, StandardCharsets.UTF_8);
                return target;
            } catch (IOException writeFailure) {
                throw new IllegalStateException("Could not write " + target.toAbsolutePath(), writeFailure);
            }
        }

        private String formatMessage(
                FlowDialect dialect, List<Difference> differences, String actual, Path writtenTo) {
            String newLine = System.lineSeparator();
            StringBuilder message = new StringBuilder();
            message.append("Flow diff found ").append(differences.size()).append(" difference(s) against ")
                    .append(expectedResource == null ? "the expected content" : expectedResource)
                    .append(" (").append(dialect.name()).append("):");
            for (Difference difference : differences) {
                message.append(newLine).append("  [chain ").append(difference.chainIndex() + 1);
                if (difference.expectedLineNumber() > 0) {
                    message.append(", expected line ").append(difference.expectedLineNumber());
                }
                message.append("] ").append(difference.kind())
                        .append(newLine).append("        expected: ").append(difference.expected())
                        .append(newLine).append("          actual: ").append(difference.actual());
            }
            message.append(newLine).append(newLine)
                    .append("recorded flow (").append(dialect.name()).append("):").append(newLine)
                    .append(annotated(actual, differences));
            if (writtenTo != null) {
                message.append(newLine).append("recorded diagram written to: ")
                        .append(writtenTo.toAbsolutePath());
            }
            return message.toString();
        }

        private static String annotated(String actual, List<Difference> differences) {
            Set<Integer> markedLines = new LinkedHashSet<>();
            for (Difference difference : differences) {
                if (difference.actualLineNumber() > 0) {
                    markedLines.add(difference.actualLineNumber());
                }
            }
            String newLine = System.lineSeparator();
            StringBuilder annotated = new StringBuilder();
            String[] lines = actual.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                int lineNumber = i + 1;
                if (lineNumber == lines.length && lines[i].isEmpty()) {
                    break;
                }
                annotated.append(markedLines.contains(lineNumber) ? ">" : " ")
                        .append(String.format("%4d  ", lineNumber))
                        .append(lines[i])
                        .append(newLine);
            }
            return annotated.toString();
        }
    }
}
