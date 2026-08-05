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
package org.os890.jawelte.module.flowassert.impl.adapter.config;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.flowassert.api.EnableFlowAssert;
import org.os890.jawelte.module.flowassert.api.FlowAssertConfig;

/**
 * Translates {@link EnableFlowAssert} into the {@code cdi-flow.*}
 * values the recorder reads while the container boots.
 *
 * <p>The recorder loads its configuration through MicroProfile Config
 * in its {@code BeforeBeanDiscovery} observer — which happens inside
 * jawelte's bootstrap window, the one stretch in which
 * {@link TestContext#get()} resolves. That is what makes a per-test-class
 * annotation reachable by a library that only knows about
 * configuration keys.
 *
 * <p>The derived map is cached per test class rather than globally:
 * one container is booted per test class, and keying the cache on the
 * class means a stale map from the previous class can never be used.
 *
 * <p>Without the annotation the answer is {@code cdi-flow.enabled=false} —
 * the recorder is on the classpath of every consumer of this module,
 * and a test class that did not ask for a recording must not get its
 * beans instrumented.
 *
 * <p>{@code abstract} plus a private constructor per the project's
 * static-utility class convention.
 */
public abstract class FlowRecordingSettings {

    private static final String PREFIX = "cdi-flow.";

    private static final Map<String, String> DISABLED = Map.of(PREFIX + "enabled", "false");

    private static volatile Class<?> cachedFor;

    private static volatile Map<String, String> cached = DISABLED;

    private FlowRecordingSettings() {
    }

    /**
     * The {@code cdi-flow.*} values that apply to the test class
     * currently being bootstrapped.
     *
     * @return the values; never {@code null}, never empty
     */
    public static Map<String, String> current() {
        Class<?> testClass = currentTestClass();
        if (testClass == null) {
            return DISABLED;
        }
        if (testClass.equals(cachedFor)) {
            return cached;
        }
        Map<String, String> computed = derive(testClass);
        cached = computed;
        cachedFor = testClass;
        return computed;
    }

    /**
     * Forget the cached values, so the next lookup derives them again.
     * Called when a test class is done with.
     */
    public static void reset() {
        cachedFor = null;
        cached = DISABLED;
    }

    private static Class<?> currentTestClass() {
        try {
            return TestContext.get().getTestClass();
        } catch (RuntimeException outsideBootstrapWindow) {
            // a container booted by something other than jawelte, or a lookup after
            // beforeAll returned: neither can name a test class, and neither should
            // switch the recorder on
            return null;
        }
    }

    private static Map<String, String> derive(Class<?> testClass) {
        EnableFlowAssert annotation = testClass.getAnnotation(EnableFlowAssert.class);
        if (annotation == null) {
            return DISABLED;
        }

        Map<String, String> settings = new LinkedHashMap<>();
        settings.put(PREFIX + "enabled", "true");
        settings.put(PREFIX + "fold-loops", String.valueOf(annotation.foldLoops()));
        if (!annotation.include().isEmpty()) {
            settings.put(PREFIX + "include-pattern", annotation.include());
        }
        String excludePattern = excludePatternFor(testClass, annotation);
        if (!excludePattern.isEmpty()) {
            settings.put(PREFIX + "exclude-pattern", excludePattern);
        }
        String stereotypes = stereotypesOf(annotation);
        if (!stereotypes.isEmpty()) {
            settings.put(PREFIX + "include-stereotypes", stereotypes);
        }
        if (annotation.hotspotThresholdMillis() >= 0) {
            settings.put(PREFIX + "hotspot-threshold-ms",
                    String.valueOf(annotation.hotspotThresholdMillis()));
        }
        if (annotation.writeTo().isEmpty()) {
            settings.put(PREFIX + "write-files", "false");
        } else {
            settings.put(PREFIX + "write-files", "true");
            settings.put(PREFIX + "output-directory", annotation.writeTo());
        }
        return Map.copyOf(settings);
    }

    private static String excludePatternFor(Class<?> testClass, EnableFlowAssert annotation) {
        List<String> patterns = new ArrayList<>(FlowAssertConfig.list(
                FlowAssertConfig.EXCLUDE_DEFAULTS_KEY));
        patterns.addAll(List.of(annotation.exclude()));
        if (!annotation.recordTestClass()) {
            patterns.add(Pattern.quote(testClass.getName()));
        }
        if (patterns.isEmpty()) {
            return "";
        }
        // the recorder matches the exclude-pattern against the whole class-name, so
        // the alternatives are grouped rather than concatenated
        List<String> grouped = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            grouped.add("(?:" + pattern + ")");
        }
        return String.join("|", grouped);
    }

    private static String stereotypesOf(EnableFlowAssert annotation) {
        List<String> names = new ArrayList<>();
        for (Class<? extends Annotation> stereotype : annotation.stereotypes()) {
            names.add(stereotype.getName());
        }
        return String.join(",", names);
    }
}
