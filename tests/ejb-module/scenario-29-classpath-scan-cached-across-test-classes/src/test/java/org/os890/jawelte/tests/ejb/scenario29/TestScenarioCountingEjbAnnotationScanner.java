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
package org.os890.jawelte.tests.ejb.scenario29;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.ejb.impl.XbeanFinderEjbAnnotationScanner;

/**
 * Test-only {@code EjbAnnotationScanner} that behaves exactly like the
 * shipped default but counts how often each layer runs.
 *
 * <p>Registered via this module's own {@code META-INF/services} at
 * {@code @Priority(Integer.MAX_VALUE - 1)}, so it outranks the shipped
 * {@code XbeanFinderEjbAnnotationScanner}
 * ({@code @Priority(Integer.MAX_VALUE)}) and the extension resolves
 * this one instead.
 *
 * <p>Two counters, and the distinction between them is the whole
 * point:
 * <ul>
 *   <li>{@link #scanCount()} — how many times the extension asked for
 *       a scan, i.e. once per container boot, so once per test class
 *       in this module.</li>
 *   <li>{@link #classpathWalkCount()} — how many times the actual
 *       classpath walk ran. The cache lives in the superclass's
 *       {@code scan(...)}, and {@code performScan(...)} is only
 *       reached on a miss, so this counts cache misses.</li>
 * </ul>
 *
 * <p>Counters are static because the extension constructs the scanner
 * through {@code ServiceLoader} per container boot — a per-instance
 * counter would reset with every test class and measure nothing.
 * Deliberately never reset: the assertions are written as "exactly one
 * walk across the whole module", which holds regardless of how many
 * test classes run or in what order, so adding another test class here
 * later cannot make the suite go red.
 */
@Priority(Integer.MAX_VALUE - 1)
public class TestScenarioCountingEjbAnnotationScanner extends XbeanFinderEjbAnnotationScanner {

    private static final AtomicInteger SCAN_COUNT = new AtomicInteger();

    private static final AtomicInteger CLASSPATH_WALK_COUNT = new AtomicInteger();

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public TestScenarioCountingEjbAnnotationScanner() {
    }

    /** @return how many times the extension requested a scan */
    public static int scanCount() {
        return SCAN_COUNT.get();
    }

    /** @return how many times the underlying classpath walk actually ran */
    public static int classpathWalkCount() {
        return CLASSPATH_WALK_COUNT.get();
    }

    @Override
    public Set<Class<?>> scan(Set<Class<? extends Annotation>> beanDefiningAnnotations,
                              Set<String> excludedPackagePrefixes) {
        SCAN_COUNT.incrementAndGet();
        return super.scan(beanDefiningAnnotations, excludedPackagePrefixes);
    }

    @Override
    protected Set<Class<?>> performScan(Set<Class<? extends Annotation>> beanDefiningAnnotations,
                                        Set<String> excludedPackagePrefixes,
                                        ClassLoader classLoader) {
        CLASSPATH_WALK_COUNT.incrementAndGet();
        return super.performScan(beanDefiningAnnotations, excludedPackagePrefixes, classLoader);
    }
}
