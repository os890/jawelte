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
package org.os890.jawelte.module.cdi.impl.adapter.mock;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;

import org.mockito.Mockito;
import org.os890.jawelte.module.cdi.api.port.MockFactory;

/**
 * Default {@link MockFactory}: {@code Mockito.mock(Class)}, answering
 * {@code null} when Mockito refuses a type. The auto-mock loop then
 * leaves the injection point alone, and CDI's own deployment validation
 * surfaces it.
 *
 * <p><b>A refusal is now reported.</b> {@code null} is a legitimate
 * answer in the port's contract — it means "not mockable, skip it" — and
 * it is the right answer for a type that genuinely cannot be
 * instrumented. It is the wrong answer to give <em>silently</em> when the
 * mocking library itself is unusable, because then every type is
 * refused, auto-mocking is off for the whole suite, and nothing says so.
 * That cost three rounds of misdiagnosis on #124, where a bug the
 * reporter hit immediately could not be reproduced here.
 *
 * <p>The two cases are told apart by asking Mockito to mock a trivial
 * private interface of this class's own. Any working Mockito can do
 * that; if even that fails, the problem is the library or the
 * environment rather than the type:
 *
 * <ul>
 *   <li><b>Library unusable</b> — reported once at {@link Level#ERROR},
 *       because it means no injection point in this deployment is being
 *       mocked. The usual cause is the inline mock maker failing to
 *       self-attach its agent, so the message names the remedy the tree
 *       already uses in sixty scenarios: {@code mock-maker-subclass} in
 *       {@code src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker}.</li>
 *   <li><b>Type refused</b> — reported at {@link Level#WARNING}, once
 *       per type, naming the type and Mockito's own message. Repeats are
 *       suppressed so a type injected in fifty places is reported
 *       once.</li>
 * </ul>
 *
 * <p>Neither case throws by default: a refusal is a normal outcome of
 * the contract, and failing the bootstrap would break suites that rely
 * on the current behaviour. A suite that wants auto-mocking to be
 * load-bearing sets
 * {@value #FAIL_ON_REFUSAL_KEY}{@code =true}, and then a refusal is an
 * {@link IllegalStateException} naming the type instead of a log line.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} so any user-supplied
 * {@link MockFactory} at a lower priority wins through the project-wide
 * {@code ServicePriorityResolver}.
 */
@Priority(Integer.MAX_VALUE)
public class MockitoMockFactory implements MockFactory {

    /**
     * MicroProfile Config key turning a refusal into a hard failure.
     * Read reflectively through {@code ConfigProvider} so this class
     * keeps working when no config source is present.
     */
    public static final String FAIL_ON_REFUSAL_KEY =
            "org.os890.jawelte.module.cdi.auto-mock.fail-on-unmockable";

    private static final Logger LOG = System.getLogger(MockitoMockFactory.class.getName());

    /** Types already reported, so a repeated injection point is quiet. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /** Guards the one-shot "library unusable" report. */
    private static final Set<String> LIBRARY_REPORT = ConcurrentHashMap.newKeySet();

    /** No-arg constructor required by {@code ServiceLoader}. */
    public MockitoMockFactory() {
    }

    @Override
    public <T> T create(Class<T> rawType) {
        try {
            return Mockito.mock(rawType);
        } catch (RuntimeException | LinkageError refused) {
            report(rawType, refused);
            if (failOnRefusal()) {
                throw new IllegalStateException(
                        "Mockito refused to mock " + rawType.getName() + " and "
                                + FAIL_ON_REFUSAL_KEY + " is set, so the auto-mock is not "
                                + "silently skipped. Register a MockFactory that handles this "
                                + "type, exclude it from auto-mocking, or satisfy the injection "
                                + "point with a real bean.", refused);
            }
            return null;
        }
    }

    /**
     * Report a refusal once, distinguishing an unusable library from an
     * unmockable type.
     */
    private static void report(Class<?> rawType, Throwable refused) {
        if (!mockingUsable()) {
            if (LIBRARY_REPORT.add("reported")) {
                LOG.log(Level.ERROR, () ->
                        "Mockito cannot mock anything in this JVM, so auto-mocking is off for "
                                + "this deployment and every unsatisfied injection point is being "
                                + "left alone silently - including " + rawType.getName() + ". "
                                + "Usually the inline mock maker cannot self-attach its agent; "
                                + "putting 'mock-maker-subclass' in "
                                + "src/test/resources/mockito-extensions/"
                                + "org.mockito.plugins.MockMaker selects the subclass mock maker, "
                                + "which needs no agent. Refusal was: " + refused);
            }
            return;
        }
        if (REPORTED.add(rawType.getName())) {
            LOG.log(Level.WARNING, () ->
                    "Mockito refused to mock " + rawType.getName() + ", so no auto-mock was "
                            + "registered for it and the injection point is left to CDI's own "
                            + "validation: " + refused);
        }
    }

    /**
     * Whether Mockito can mock anything at all, probed once against a
     * trivial interface of this class's own.
     *
     * @return {@code false} when even that fails, which means the
     *         library or the environment is the problem rather than the
     *         type that was asked for
     */
    private static boolean mockingUsable() {
        try {
            return Mockito.mock(Probe.class) != null;
        } catch (RuntimeException | LinkageError unusable) {
            return false;
        }
    }

    /** Read the strict-mode key, defaulting to {@code false}. */
    private static boolean failOnRefusal() {
        try {
            return org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getOptionalValue(FAIL_ON_REFUSAL_KEY, Boolean.class)
                    .orElse(Boolean.FALSE);
        } catch (RuntimeException noConfigSource) {
            return false;
        }
    }

    /** The trivial type {@link #mockingUsable()} probes with. */
    private interface Probe {
    }
}
