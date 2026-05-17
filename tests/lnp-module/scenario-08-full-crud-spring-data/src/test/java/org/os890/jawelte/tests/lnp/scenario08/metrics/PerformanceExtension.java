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
package org.os890.jawelte.tests.lnp.scenario08.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit Jupiter extension that collects per-method and per-class
 * timing and heap-usage metrics, then prints a summary after all
 * test classes have finished. Results are kept in a static list so
 * the load-and-performance scenario can aggregate across every
 * subclass run within the same test JVM.
 */
public class PerformanceExtension
        implements BeforeAllCallback, AfterAllCallback,
                   BeforeEachCallback, AfterEachCallback {

    private static final List<ClassResult> CLASS_RESULTS =
            Collections.synchronizedList(new ArrayList<>());

    private static final String CLASS_START_NS = "perf.class.startNs";
    private static final String CLASS_HEAP_START = "perf.class.heapStart";
    private static final String METHOD_START_NS = "perf.method.startNs";
    private static final String METHOD_DURATIONS = "perf.method.durations";

    /** Default no-arg constructor used by JUnit's ServiceLoader-style discovery. */
    public PerformanceExtension() {
    }

    @Override
    public void beforeAll(ExtensionContext ctx) {
        store(ctx).put(CLASS_START_NS, System.nanoTime());
        store(ctx).put(CLASS_HEAP_START, heapUsedBytes());
        store(ctx).put(METHOD_DURATIONS, new ArrayList<Long>());
    }

    @Override
    public void beforeEach(ExtensionContext ctx) {
        store(ctx).put(METHOD_START_NS, System.nanoTime());
    }

    @Override
    public void afterEach(ExtensionContext ctx) {
        long startNs = store(ctx).remove(METHOD_START_NS, long.class);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;

        @SuppressWarnings("unchecked")
        List<Long> durations = (List<Long>) store(ctx.getParent().orElse(ctx))
                .get(METHOD_DURATIONS);
        if (durations != null) {
            durations.add(durationMs);
        }
    }

    @Override
    public void afterAll(ExtensionContext ctx) {
        long classStartNs = store(ctx).remove(CLASS_START_NS, long.class);
        long heapStart = store(ctx).remove(CLASS_HEAP_START, long.class);
        long totalMs = (System.nanoTime() - classStartNs) / 1_000_000L;
        long heapEnd = heapUsedBytes();

        @SuppressWarnings("unchecked")
        List<Long> durations = (List<Long>) store(ctx).remove(METHOD_DURATIONS);

        String className = ctx.getRequiredTestClass().getSimpleName();
        int methodCount = (durations != null) ? durations.size() : 0;
        long medianMs = median(durations);
        long heapDelta = heapEnd - heapStart;

        ClassResult result = new ClassResult(
                className, methodCount, totalMs, medianMs,
                heapStart, heapEnd, heapDelta);

        CLASS_RESULTS.add(result);

        System.out.printf(
                "[perf] %s  methods=%d  total=%dms  median=%dms  heap-delta=%+.1fMB  threads=%d%n",
                className, methodCount, totalMs, medianMs,
                heapDelta / (1024.0 * 1024.0),
                ManagementFactory.getThreadMXBean().getThreadCount());
    }

    /**
     * Prints a formatted table summarising every test class that was
     * instrumented by this extension during the current test JVM.
     * Intended to be called from a sentinel test ordered to run last.
     */
    public static void printFinalSummary() {
        if (CLASS_RESULTS.isEmpty()) {
            System.out.println("[perf] No performance data collected.");
            return;
        }

        String header = String.format(
                "%-40s %7s %12s %14s %12s %12s %12s",
                "Class", "Methods", "Total ms", "Median ms",
                "Heap-Start MB", "Heap-End MB", "Heap-Delta MB");

        System.out.println();
        System.out.println("=== LNP Performance Summary (spring-data) ===");
        System.out.println(header);
        System.out.println("-".repeat(header.length()));

        for (ClassResult r : CLASS_RESULTS) {
            System.out.printf(
                    "%-40s %7d %12d %14d %12.1f %12.1f %12.1f%n",
                    r.className(), r.methodCount(), r.totalMs(),
                    r.medianMs(),
                    r.heapStartBytes() / (1024.0 * 1024.0),
                    r.heapEndBytes() / (1024.0 * 1024.0),
                    r.heapDeltaBytes() / (1024.0 * 1024.0));
        }

        System.out.println("-".repeat(header.length()));
        System.out.println();
    }

    private static ExtensionContext.Store store(ExtensionContext ctx) {
        return ctx.getStore(
                ExtensionContext.Namespace.create(PerformanceExtension.class));
    }

    private static long heapUsedBytes() {
        // Hint a GC + brief settle so the reading reflects retained
        // memory rather than transient garbage. Under OWB the raw
        // used-bytes reading drifts upward 2-3x faster than under Weld
        // simply because more short-lived allocations accumulate
        // between Full GCs; this hint normalises the two runtimes.
        System.gc();
        try {
            Thread.sleep(20);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        MemoryUsage heap = ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage();
        return heap.getUsed();
    }

    private static long median(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2;
        }
        return sorted.get(mid);
    }

    /**
     * Captured per-class metrics: number of methods executed, total
     * wall time, median per-method wall time, and heap-usage snapshots
     * taken at class boot and class teardown.
     *
     * @param className simple name of the test class
     * @param methodCount number of test methods executed
     * @param totalMs total wall-clock time across the class in ms
     * @param medianMs median per-method wall-clock time in ms
     * @param heapStartBytes JVM heap-used bytes at beforeAll
     * @param heapEndBytes JVM heap-used bytes at afterAll
     * @param heapDeltaBytes signed difference of heapEnd minus heapStart
     */
    record ClassResult(
                String className,
                int methodCount,
                long totalMs,
                long medianMs,
                long heapStartBytes,
                long heapEndBytes,
                long heapDeltaBytes) {
    }
}
