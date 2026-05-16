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
package org.os890.jawelte.core.impl.adapter.extension;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Prints the jawelte boot banner to {@code System.out} when the JUnit
 * launcher opens its session - i.e. once per test JVM, before any
 * test class boots.
 *
 * <p>Registered through
 * {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}
 * so JUnit's {@code ServiceLoader} discovery instantiates and invokes
 * it automatically.
 *
 * <p>Uses {@link System#out} directly rather than {@code System.Logger}:
 * the banner's visual layout depends on raw line output without
 * level/timestamp prefixes that a logger backend would inject, and the
 * banner has to render before any module's logger configuration takes
 * effect.
 */
public class BootBanner implements LauncherSessionListener {

    private static final String BANNER =
            System.lineSeparator()
            + "     ██╗ █████╗ ██╗    ██╗███████╗██╗  ████████╗███████╗" + System.lineSeparator()
            + "     ██║██╔══██╗██║    ██║██╔════╝██║  ╚══██╔══╝██╔════╝" + System.lineSeparator()
            + "     ██║███████║██║ █╗ ██║█████╗  ██║     ██║   █████╗  " + System.lineSeparator()
            + "██   ██║██╔══██║██║███╗██║██╔══╝  ██║     ██║   ██╔══╝  " + System.lineSeparator()
            + "╚█████╔╝██║  ██║╚███╔███╔╝███████╗███████╗██║   ███████╗" + System.lineSeparator()
            + " ╚════╝ ╚═╝  ╚═╝ ╚══╝╚══╝ ╚══════╝╚══════╝╚═╝   ╚══════╝" + System.lineSeparator()
            + "       JUnit 6  ·  CDI SE  ·  Jakarta EE 11" + System.lineSeparator();

    /**
     * No-arg constructor used by {@code ServiceLoader}.
     */
    public BootBanner() {
    }

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        System.out.print(BANNER);
    }
}
