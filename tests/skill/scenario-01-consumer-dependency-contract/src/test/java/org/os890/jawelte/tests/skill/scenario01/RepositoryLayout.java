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
package org.os890.jawelte.tests.skill.scenario01;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the project's own poms.
 *
 * <p>The claims under test are about scopes and edges declared in xml,
 * not about anything observable from a running container, so the poms
 * are the subject. The repository root is found by walking up from the
 * working directory rather than taken from a property, so the scenario
 * behaves the same in the default reactor, in {@code -Pfull-reactor}
 * and under {@code verify-all/pom.xml}.
 */
class RepositoryLayout {

    private static final Pattern DEPENDENCY = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL);
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>(.*?)</artifactId>");
    private static final Pattern SCOPE = Pattern.compile("<scope>(.*?)</scope>");

    private RepositoryLayout() {
    }

    static Path root() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("modules"))
                    && Files.isDirectory(cursor.resolve("core"))
                    && Files.isRegularFile(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException(
                "could not locate the repository root above " + Path.of("").toAbsolutePath());
    }

    static String read(Path pom) {
        try {
            return Files.readString(pom, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + pom, e);
        }
    }

    /**
     * The {@code <dependencyManagement>} block of a pom, as artifactId
     * to declared scope. An entry without an explicit scope maps to
     * {@code "compile"}, which is what Maven defaults it to.
     *
     * @param pomContent the pom source
     * @return managed artifactId to scope
     */
    static Map<String, String> managedScopes(String pomContent) {
        int start = pomContent.indexOf("<dependencyManagement>");
        int end = pomContent.indexOf("</dependencyManagement>");
        if (start < 0 || end < 0) {
            throw new IllegalStateException("no <dependencyManagement> in the given pom");
        }
        Map<String, String> scopes = new LinkedHashMap<>();
        Matcher dependency = DEPENDENCY.matcher(pomContent.substring(start, end));
        while (dependency.find()) {
            String block = dependency.group(1);
            Matcher artifactId = ARTIFACT_ID.matcher(block);
            if (!artifactId.find()) {
                continue;
            }
            Matcher scope = SCOPE.matcher(block);
            scopes.put(artifactId.group(1), scope.find() ? scope.group(1) : "compile");
        }
        return scopes;
    }

    /**
     * The artifactIds a pom declares in its own {@code <dependencies>}
     * block, ignoring anything inside {@code <dependencyManagement>}
     * and inside {@code <profiles>}.
     *
     * @param pomContent the pom source
     * @return declared artifactIds, in declaration order
     */
    static List<String> declaredDependencies(String pomContent) {
        String withoutManagement = removeBlock(pomContent, "dependencyManagement");
        String withoutProfiles = removeBlock(withoutManagement, "profiles");
        List<String> artifactIds = new ArrayList<>();
        Matcher dependency = DEPENDENCY.matcher(withoutProfiles);
        while (dependency.find()) {
            Matcher artifactId = ARTIFACT_ID.matcher(dependency.group(1));
            if (artifactId.find()) {
                artifactIds.add(artifactId.group(1));
            }
        }
        return artifactIds;
    }

    /**
     * The scope a module's dependency actually ends up with: the one
     * the module declares if it declares one, otherwise the one
     * {@code jawelte-parent} manages it at.
     *
     * <p>Both levels matter. {@code xbean-naming} and
     * {@code hibernate-core} are managed at {@code test} but overridden
     * to {@code provided} by the module that needs them, and only the
     * effective value decides whether a consumer inherits the library
     * or has to declare it.
     *
     * @param pomContent  the module's pom source
     * @param parentPom   jawelte-parent's pom source
     * @param artifactId  the dependency to resolve
     * @return the effective scope
     */
    static String effectiveScope(String pomContent, String parentPom, String artifactId) {
        String withoutManagement = removeBlock(pomContent, "dependencyManagement");
        String withoutProfiles = removeBlock(withoutManagement, "profiles");
        Matcher dependency = DEPENDENCY.matcher(withoutProfiles);
        while (dependency.find()) {
            String block = dependency.group(1);
            Matcher declared = ARTIFACT_ID.matcher(block);
            if (!declared.find() || !declared.group(1).equals(artifactId)) {
                continue;
            }
            Matcher scope = SCOPE.matcher(block);
            if (scope.find()) {
                return scope.group(1);
            }
            break;
        }
        String managed = managedScopes(parentPom).get(artifactId);
        if (managed == null) {
            throw new IllegalStateException(artifactId + " is neither declared nor managed");
        }
        return managed;
    }

    private static String removeBlock(String content, String element) {
        int start = content.indexOf("<" + element + ">");
        int end = content.indexOf("</" + element + ">");
        if (start < 0 || end < 0) {
            return content;
        }
        return content.substring(0, start) + content.substring(end + element.length() + 3);
    }
}
