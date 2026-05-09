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
package org.os890.jawelte.module.jpa.impl.launcher;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.os890.jawelte.module.jpa.impl.adapter.scan.XbeanFinderEntityScanner;
import org.os890.jawelte.module.jpa.impl.util.EmfCache;
import org.os890.jawelte.module.jpa.impl.util.JpaActivePersistenceUnits;
import org.os890.jawelte.module.jpa.impl.util.TransactionScopedEmHolder;

/**
 * Opt-in JUnit Platform {@link LauncherSessionListener} that gives
 * jpa-module a deterministic JVM-scoped lifecycle. Fires once per
 * launcher session (≈ once per JVM regardless of how Maven Surefire
 * forks): on session open it pre-warms the
 * {@link XbeanFinderEntityScanner} cache so the first test class
 * doesn't pay classpath-walk latency, and on session close (after
 * every test class in the JVM has finished) it closes every cached
 * {@link jakarta.persistence.EntityManagerFactory} (releasing H2
 * file-mode locks deterministically before the JVM shutdown hook
 * runs), drops the scanner cache, resets
 * {@link JpaActivePersistenceUnits}, and drains
 * {@link TransactionScopedEmHolder} for the calling thread.
 *
 * <p><strong>Not registered by default.</strong> jpa-module ships no
 * {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}
 * resource, so the default path is the existing JVM shutdown hook
 * registered by {@link EmfCache} on first use plus per-class lazy
 * scanning. Consumers who want JVM-scoped lifecycle precision —
 * deterministic file-lock release between Surefire suites, predictable
 * scan timing across test classes, resource budgeting in CI — opt in
 * by adding their own {@code META-INF/services} entry pointing at this
 * class.
 *
 * <p>Best-effort and never throws: pre-warm failures fall back to the
 * lazy scan with the same diagnostic, EMF close failures aggregate
 * inside {@link EmfCache#closeAll()} and log at {@code WARNING}.
 * {@link #OPEN_COUNT} and {@link #CLOSE_COUNT} expose invocation counts
 * so opt-in tests can assert the listener actually fired and drive
 * {@link #deactivate()} imperatively when the post-session timing of
 * the real close hook puts it out of reach of in-test observation.
 */
public class JpaLauncherSessionListener implements LauncherSessionListener {

    /** Number of {@link #launcherSessionOpened(LauncherSession)} fires across the JVM. */
    public static final AtomicInteger OPEN_COUNT = new AtomicInteger();

    /** Number of {@link #launcherSessionClosed(LauncherSession)} fires across the JVM. */
    public static final AtomicInteger CLOSE_COUNT = new AtomicInteger();

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JpaLauncherSessionListener() {
    }

    /**
     * Run the same logic
     * {@link #launcherSessionOpened(LauncherSession)} runs, without
     * needing a real {@code LauncherSession} instance. Lets opt-in
     * tests exercise the open path imperatively.
     */
    public static void activate() {
        OPEN_COUNT.incrementAndGet();
        XbeanFinderEntityScanner.prewarmForCurrentThread();
    }

    /**
     * Run the same logic
     * {@link #launcherSessionClosed(LauncherSession)} runs, without
     * needing a real {@code LauncherSession} instance. Lets opt-in
     * tests exercise the close path imperatively.
     */
    public static void deactivate() {
        CLOSE_COUNT.incrementAndGet();
        EmfCache.closeAll();
        XbeanFinderEntityScanner.clearScanCache();
        JpaActivePersistenceUnits.reset();
        TransactionScopedEmHolder.clearForCurrentThread();
    }

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        activate();
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        deactivate();
    }
}
