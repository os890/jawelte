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
package org.os890.jawelte.tests.jpa.scenario27;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Drives {@link Scenario27FileModeSubject} via JUnit Platform Test Kit.
 * The subject is annotated {@code @PersistenceConfig(fileMode = true)}
 * with an empty {@code filePath}, so jpa-module's default-path branch
 * resolves the H2 file directory to {@code ~/<TestClass>_db}. After the
 * kit run, this test verifies the directory and the H2 {@code .mv.db}
 * file were created at the expected location and cleans them up.
 */
public class Scenario27Test {

    private static final Path EXPECTED_DEFAULT_DB_DIR =
            Path.of(System.getProperty("user.home"), "Scenario27FileModeSubject_db");

    /** No-arg constructor required by JUnit. */
    public Scenario27Test() {
    }

    /** Subject runs successfully + creates files at ~/Scenario27FileModeSubject_db/testPU27.mv.db. */
    @Test
    public void fileModeDefaultPathResolvesToHomeDirSlashTestClassUnderscoreDb() throws IOException {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario27FileModeSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        assertThat(EXPECTED_DEFAULT_DB_DIR)
                .as("the default-path branch must create the directory ~/<TestClass>_db")
                .exists()
                .isDirectory();

        // H2's URL is jdbc:h2:file:<filePath>/<puName>_<TestClass.simpleName>, so
        // H2 writes <filePath>/<puName>_<TestClass.simpleName>.mv.db.
        Path mvDb = EXPECTED_DEFAULT_DB_DIR.resolve("testPU27_Scenario27FileModeSubject.mv.db");
        assertThat(mvDb)
                .as("H2 must write the .mv.db file at <default-path>/<puName>_<TestClass>.mv.db")
                .exists()
                .isRegularFile();
    }

    /** Cleanup: remove the test-created database directory + every file beneath it. */
    @AfterAll
    public static void cleanupDefaultDbDirectory() throws IOException {
        if (!Files.exists(EXPECTED_DEFAULT_DB_DIR)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(EXPECTED_DEFAULT_DB_DIR)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup; CI runs are ephemeral so leftover files don't harm
                }
            });
        }
    }
}
