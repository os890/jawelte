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
package org.os890.jawelte.module.testcontrol.impl.adapter.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.CDI;

import org.os890.jawelte.core.api.event.AfterTestTransaction;
import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * {@code @ApplicationScoped} CDI bean that owns the per-method
 * test-data pipeline declared by {@code @TestControl(testData=…)}.
 * The pipeline has four phases:
 *
 * <ol>
 *   <li><b>Seed</b> — for every entry in array order, call
 *       {@code DbSeed.forPersistenceUnit(…).dataset(xml).cleanInsert().execute()}
 *       on each {@code *.xml} found in the entry's {@code dbIn/}
 *       sub-folder (alphabetical order of file names).</li>
 *   <li><b>Update</b> — same loop but on {@code dbUpdate/} with
 *       {@code .update().execute()}. All {@code dbIn/} across all
 *       entries completes before any {@code dbUpdate/} starts.</li>
 *   <li><b>Commit</b> — each phase runs inside a short-lived
 *       {@code @Transactional} call to a
 *       {@link TestDataSeedTransactionTemplate} CDI bean, one
 *       transaction per distinct persistence unit per phase. The
 *       interceptor (provided by jta-module's active
 *       {@code TransactionStrategy}) commits on lambda return,
 *       making the seed data durable and visible to other threads
 *       before the test method's own transaction begins. The seed
 *       transactions are independent of any
 *       {@code @Transactional} on the test method itself.</li>
 *   <li><b>Verify</b> — for every entry, call
 *       {@code DbDiff.forPersistenceUnit(…).expected(xml).assertEquals()}
 *       on each {@code *.xml} in the entry's {@code dbExpected/}
 *       sub-folder. Runs <em>after</em> the test method completes —
 *       either through the {@link AfterTestTransaction} observer
 *       (transactional path) or through testcontrol's own
 *       {@code afterEach} fallback (non-transactional path).</li>
 * </ol>
 *
 * <p><b>Phases 1–3 entry points.</b> The lifecycle adapter calls
 * {@link #seedAll(TestControl)} in {@code beforeEach}.
 *
 * <p><b>Phase 4 entry points.</b> Two:
 *
 * <ul>
 *   <li>{@link #onAfterTestTransaction(AfterTestTransaction)} —
 *       fired by jpa-module's adapter at {@code @Priority(200)}
 *       inside its {@code afterEach}, after the transaction has been
 *       committed or rolled back and before JPA table cleanup. The
 *       observer dispatches to {@link #verifyAll()} and the handler
 *       sets {@link #didAlreadyVerify()} so the lifecycle adapter's
 *       {@code afterEach} fallback skips the second call.</li>
 *   <li>{@link #verifyAll()} — invoked from the lifecycle adapter's
 *       {@code afterEach} as the non-transactional fallback, but
 *       only when {@link #didAlreadyVerify()} is still
 *       {@code false}.</li>
 * </ul>
 *
 * <p><b>Folder enumeration.</b> Resolves each entry's classpath
 * folder via
 * {@code Thread.currentThread().getContextClassLoader().getResource(folderPath)}.
 * Supports both {@code file:} and {@code jar:} URL protocols. Missing
 * the entry's base folder raises
 * {@link IllegalArgumentException}; missing the {@code dbIn/} /
 * {@code dbUpdate/} / {@code dbExpected/} sub-folder is silent (no
 * work for that phase, no error).
 *
 * <p><b>Base-path resolution.</b> The MicroProfile Config key
 * {@code org.os890.jawelte.module.testcontrol.api.TestControl.base-path}
 * wins over the {@code testDataBasePath} annotation attribute when
 * set to a non-empty value; otherwise the annotation attribute is
 * used; otherwise the empty string. The resolved prefix is prepended
 * to every entry's folder path (after the {@code puName:} prefix is
 * stripped).
 *
 * <p><b>State.</b> Per-method state lives in two {@code volatile}
 * fields on this {@code @ApplicationScoped} bean
 * ({@link #activeAnnotation}, {@link #verifiedThisMethod}); the
 * lifecycle adapter calls {@link #clearActive()} at the end of
 * {@code afterEach} so the next test method starts with a clean
 * slate. Thread-safe for the project's single-test-thread model;
 * not safe for parallel test methods.
 */
@ApplicationScoped
public class TestDataHandler {

    /**
     * MicroProfile Config key that overrides
     * {@code TestControl#testDataBasePath()}.
     */
    public static final String BASE_PATH_CONFIG_KEY =
            "org.os890.jawelte.module.testcontrol.api.TestControl.base-path";

    private static final String DB_IN = "dbIn";
    private static final String DB_UPDATE = "dbUpdate";
    private static final String DB_EXPECTED = "dbExpected";
    private static final String XML_SUFFIX = ".xml";

    private volatile TestControl activeAnnotation;
    private volatile boolean verifiedThisMethod;

    /** No-arg constructor required by the CDI runtime. */
    public TestDataHandler() {
    }

    /**
     * Drive the seed and update phases inside one transaction per
     * distinct persistence unit. Stores {@code annotation} on the
     * handler so the {@link AfterTestTransaction} observer can drive
     * the verify phase without re-resolving the annotation through
     * {@code TestContext} (which is not available in observer
     * dispatch — {@code TestContext.get()} throws once
     * {@code DelegatingJUnitExtension.beforeAll}'s {@code finally}
     * has cleared the per-thread accessor). No-op when
     * {@code testData} is empty.
     *
     * @param annotation  the active {@code @TestControl} on the test
     *                    method
     * @throws IllegalArgumentException when an entry's base folder is
     *                                  not on the classpath
     */
    public void seedAll(TestControl annotation) {
        // Clear first, publish last. The verify phase (the
        // AfterTestTransaction observer and the afterEach fallback) is
        // gated on activeAnnotation, so it MUST be published only once
        // seeding has fully succeeded. Publishing it up front — before the
        // guards or the seed phases can throw — would leak this method's
        // annotation: when beforeEach throws, the extension never records
        // testcontrol as completed, so its afterEach (hence clearActive())
        // never runs, and a later method's unconditional AfterTestTransaction
        // would verify against this method's stale dbExpected on this
        // @ApplicationScoped handler.
        this.activeAnnotation = null;
        this.verifiedThisMethod = false;
        if (annotation == null || annotation.testData().length == 0) {
            return;
        }
        List<EntrySpec> entries = parseEntries(annotation);
        validateBaseFolders(entries);
        if (annotation.requireDbExpected() && !anyEntryContributesDbExpected(entries)) {
            throw new IllegalStateException(
                    "@TestControl(testData=" + describeTestData(annotation)
                            + ") requires at least one dbExpected/*.xml across the listed "
                            + "entries — none was found. Either add a dbExpected/<file>.xml "
                            + "next to the dbIn/ / dbUpdate/ folders, or set "
                            + "requireDbExpected=false on the @TestControl annotation if this "
                            + "is intentionally a seed-only test method.");
        }

        TestDataSeedTransactionTemplate template =
                CDI.current().select(TestDataSeedTransactionTemplate.class).get();
        runPhase(entries, DB_IN, DbSeed.Builder::cleanInsert, template);
        runPhase(entries, DB_UPDATE, DbSeed.Builder::update, template);
        this.activeAnnotation = annotation;
    }

    private static boolean anyEntryContributesDbExpected(List<EntrySpec> entries) {
        for (EntrySpec entry : entries) {
            if (!listXmlResources(entry.folderPath() + "/" + DB_EXPECTED).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String describeTestData(TestControl annotation) {
        String[] testData = annotation.testData();
        if (testData.length == 1) {
            return "\"" + testData[0] + "\"";
        }
        StringBuilder buffer = new StringBuilder("{");
        for (int index = 0; index < testData.length; index++) {
            if (index > 0) {
                buffer.append(", ");
            }
            buffer.append('"').append(testData[index]).append('"');
        }
        return buffer.append('}').toString();
    }

    /**
     * Drive the verify phase ({@code dbExpected/}) against the
     * annotation cached in {@link #seedAll(TestControl)}.
     * Both the transactional observer path and the non-transactional
     * fallback land here. Sets {@link #verifiedThisMethod} so the
     * lifecycle adapter's {@code afterEach} can decide whether the
     * fallback is needed.
     */
    public void verifyAll() {
        TestControl annotation = activeAnnotation;
        if (annotation == null || annotation.testData().length == 0) {
            return;
        }
        try {
            List<EntrySpec> entries = parseEntries(annotation);
            validateBaseFolders(entries);
            TestDataSeedTransactionTemplate template =
                    CDI.current().select(TestDataSeedTransactionTemplate.class).get();
            Set<String> distinctPuKeys = new LinkedHashSet<>();
            for (EntrySpec entry : entries) {
                distinctPuKeys.add(puKey(entry));
            }
            for (String puKey : distinctPuKeys) {
                String puName = puKey.isEmpty() ? null : puKey;
                template.runInTransaction(puName, () -> verifyEntriesForPu(entries, puKey));
            }
        } finally {
            this.verifiedThisMethod = true;
        }
    }

    private void verifyEntriesForPu(List<EntrySpec> entries, String puKey) {
        for (EntrySpec entry : entries) {
            if (!puKey(entry).equals(puKey)) {
                continue;
            }
            for (String xml : listXmlResources(entry.folderPath() + "/" + DB_EXPECTED)) {
                diffBuilder(entry).expected(xml).assertEquals();
            }
        }
    }

    /**
     * Whether {@link #verifyAll()} has already run for the current
     * test method.
     *
     * @return {@code true} if {@code verifyAll} already executed
     *         (so the lifecycle adapter skips the {@code afterEach}
     *         fallback); {@code false} otherwise
     */
    public boolean didAlreadyVerify() {
        return verifiedThisMethod;
    }

    /**
     * Reset per-method state. Called from the lifecycle adapter's
     * {@code afterEach} so the next test method starts with a clean
     * slate even though this handler is {@code @ApplicationScoped}.
     */
    public void clearActive() {
        this.activeAnnotation = null;
        this.verifiedThisMethod = false;
    }

    void onAfterTestTransaction(@Observes AfterTestTransaction event) {
        verifyAll();
    }

    private void runPhase(List<EntrySpec> entries, String subDir,
                          Function<DbSeed.Builder, DbSeed.Builder> mode,
                          TestDataSeedTransactionTemplate template) {
        Set<String> distinctPuKeys = new LinkedHashSet<>();
        for (EntrySpec entry : entries) {
            distinctPuKeys.add(puKey(entry));
        }
        for (String puKey : distinctPuKeys) {
            String puName = puKey.isEmpty() ? null : puKey;
            template.runInTransaction(puName, () -> {
                for (EntrySpec entry : entries) {
                    if (!puKey(entry).equals(puKey)) {
                        continue;
                    }
                    for (String xml : listXmlResources(entry.folderPath() + "/" + subDir)) {
                        mode.apply(seedBuilder(entry).dataset(xml)).execute();
                    }
                }
            });
        }
    }

    private static String puKey(EntrySpec entry) {
        return entry.puName() == null ? "" : entry.puName();
    }

    private DbSeed.Builder seedBuilder(EntrySpec entry) {
        return entry.puName() == null
                ? DbSeed.forPersistenceUnit()
                : DbSeed.forPersistenceUnit(entry.puName());
    }

    private DbDiff.Builder diffBuilder(EntrySpec entry) {
        return entry.puName() == null
                ? DbDiff.forPersistenceUnit()
                : DbDiff.forPersistenceUnit(entry.puName());
    }

    private List<EntrySpec> parseEntries(TestControl annotation) {
        String basePath = resolveBasePath(annotation);
        List<EntrySpec> entries = new ArrayList<>(annotation.testData().length);
        for (String raw : annotation.testData()) {
            entries.add(EntrySpec.parse(raw, basePath));
        }
        return entries;
    }

    private static String resolveBasePath(TestControl annotation) {
        ConfigResolver resolver = TestContext.loadService(ConfigResolver.class);
        if (resolver != null) {
            Optional<String> mpValue = resolver.resolve(BASE_PATH_CONFIG_KEY);
            if (mpValue.isPresent() && !mpValue.get().isEmpty()) {
                return mpValue.get();
            }
        }
        return annotation.testDataBasePath();
    }

    private static void validateBaseFolders(List<EntrySpec> entries) {
        ClassLoader loader = contextClassLoader();
        for (EntrySpec entry : entries) {
            if (loader.getResource(entry.folderPath()) == null) {
                throw new IllegalArgumentException("Test data folder not found: " + entry.folderPath());
            }
        }
    }

    private static List<String> listXmlResources(String folderClasspath) {
        ClassLoader loader = contextClassLoader();
        URL url = loader.getResource(folderClasspath);
        if (url == null) {
            return List.of();
        }
        switch (url.getProtocol()) {
            case "file":
                return listFromFileSystem(folderClasspath, url);
            case "jar":
                return listFromJar(folderClasspath, url);
            default:
                throw new IllegalStateException(
                        "Unsupported classpath URL protocol for " + folderClasspath + ": " + url);
        }
    }

    private static List<String> listFromFileSystem(String folderClasspath, URL url) {
        Path directory;
        try {
            directory = Paths.get(url.toURI());
        } catch (URISyntaxException uriException) {
            throw new IllegalStateException("Invalid classpath URL: " + url, uriException);
        }
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(TestDataHandler::isXml)
                    .sorted()
                    .map(name -> folderClasspath + "/" + name)
                    .toList();
        } catch (IOException ioException) {
            throw new UncheckedIOException(
                    "Failed to list test-data folder " + folderClasspath, ioException);
        }
    }

    private static List<String> listFromJar(String folderClasspath, URL url) {
        URLConnection connection;
        try {
            connection = url.openConnection();
        } catch (IOException ioException) {
            throw new UncheckedIOException(
                    "Failed to open jar URL for " + folderClasspath, ioException);
        }
        if (!(connection instanceof JarURLConnection jarConnection)) {
            throw new IllegalStateException(
                    "Expected JarURLConnection for " + folderClasspath + ", got "
                            + connection.getClass().getName());
        }
        String prefix = folderClasspath.endsWith("/") ? folderClasspath : folderClasspath + "/";
        List<String> names = new ArrayList<>();
        try {
            JarFile jarFile = jarConnection.getJarFile();
            for (var iterator = jarFile.entries(); iterator.hasMoreElements();) {
                JarEntry entry = iterator.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!name.startsWith(prefix)) {
                    continue;
                }
                String remainder = name.substring(prefix.length());
                if (remainder.contains("/")) {
                    continue;
                }
                if (isXml(remainder)) {
                    names.add(name);
                }
            }
        } catch (IOException ioException) {
            throw new UncheckedIOException(
                    "Failed to read jar entries for " + folderClasspath, ioException);
        }
        java.util.Collections.sort(names);
        return names;
    }

    private static boolean isXml(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(XML_SUFFIX);
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : TestDataHandler.class.getClassLoader();
    }

    /**
     * Parsed form of one {@code @TestControl#testData()} entry. The
     * {@code puName} is the substring before the first {@code ':'}
     * (or {@code null} when the entry has no prefix); the
     * {@code folderPath} is the {@code testDataBasePath} prefix
     * (resolved per the MP-Config-wins rule) concatenated with the
     * remainder.
     *
     * @param puName     the explicit persistence-unit name, or
     *                   {@code null}
     * @param folderPath the resolved classpath folder path
     */
    record EntrySpec(String puName, String folderPath) {

        static EntrySpec parse(String raw, String basePath) {
            int colon = raw.indexOf(':');
            String puName;
            String path;
            if (colon >= 0) {
                puName = raw.substring(0, colon);
                path = raw.substring(colon + 1);
            } else {
                puName = null;
                path = raw;
            }
            String joined = joinPath(basePath, path);
            return new EntrySpec(puName, joined);
        }

        private static String joinPath(String basePath, String entryPath) {
            if (basePath == null || basePath.isEmpty()) {
                return entryPath;
            }
            String prefix = basePath.endsWith("/")
                    ? basePath.substring(0, basePath.length() - 1)
                    : basePath;
            String suffix = entryPath.startsWith("/") ? entryPath.substring(1) : entryPath;
            return prefix + "/" + suffix;
        }
    }
}
