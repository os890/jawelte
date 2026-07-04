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
package org.os890.jawelte.module.testcontrol.api;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Per-test-method convenience annotation that binds two
 * testcontrol-module features into a single annotation:
 *
 * <ul>
 *   <li><b>Scope filtering</b> via {@link #startScopes()} — restricts
 *       which per-method scope-module scope ({@code @TestMethodScoped})
 *       is activated for this test method, by vetoing it through the
 *       {@code BeforeScopeStarted} CDI event fired by scope-module's
 *       lifecycle adapter when it is not listed. See {@link #startScopes()}
 *       for the {@code @TestClassScoped} (class-lifetime) caveat.</li>
 *   <li><b>Database fixture handling</b> via {@link #testData()} —
 *       drives the four-phase pipeline
 *       (<i>seed → update → commit → verify</i>) over classpath
 *       folders that contain optional {@code dbIn/}, {@code dbUpdate/},
 *       {@code dbExpected/} sub-directories. The seed and update phases
 *       feed db-testdata-module's {@code DbSeed}; the verify phase
 *       feeds its {@code DbDiff}.</li>
 * </ul>
 *
 * <p><b>Target.</b> Method-only ({@code @Target(METHOD)}). The
 * annotation is rejected on classes by the compiler.
 *
 * <p><b>Inheritance.</b> {@link java.lang.annotation.Inherited
 * @Inherited} is deliberately <em>not</em> applied:
 * {@code java.lang.annotation.Inherited} only takes effect on
 * class-level annotations, so adding it to a {@code @Target(METHOD)}
 * annotation would be misleading. Cross-class inheritance of
 * {@code @TestControl} on a test method comes from JUnit Jupiter's
 * own {@code AnnotationSupport.findAnnotation}, which walks the test
 * class hierarchy when resolving annotations on the active test
 * method:
 *
 * <ul>
 *   <li>If a subclass <em>does not</em> override the test method,
 *       JUnit Jupiter finds the {@code @TestControl} declared on the
 *       superclass method and applies it.</li>
 *   <li>If a subclass <em>does</em> override the method (whether or
 *       not the override carries its own {@code @TestControl}), the
 *       subclass declaration wins — the superclass annotation is
 *       shadowed by the override and is <em>not</em> merged.</li>
 * </ul>
 *
 * <p><b>Base-path precedence.</b> The base path prepended to every
 * entry is resolved in this order:
 * <ol>
 *   <li>the MicroProfile Config key
 *       {@code org.os890.jawelte.module.testcontrol.api.TestControl.base-path},
 *       when set to a non-empty value;</li>
 *   <li>otherwise the {@link #testDataBasePath()} annotation
 *       attribute;</li>
 *   <li>otherwise the empty string (entry paths are used as-is).</li>
 * </ol>
 *
 * <p><b>Companion remap.</b> When scope-module is on the classpath,
 * its {@code ConfigBeanToTestClassScoped} {@code BeanScopeMapper} SPI
 * provider — consumed by core's {@code ScopeRemapCdiExtension} through
 * the {@code BeanScopeMapperPort} — remaps {@code @ConfigBean}-stereotyped
 * classes from {@code @ApplicationScoped} to {@code @TestClassScoped}
 * during CDI bootstrap. This remap is independent of
 * {@code @TestControl} — having any test method use
 * {@code @TestControl} is <em>not</em> required for the remap to occur.
 *
 * <p>There is a per-bean opt-out: a class that declares an explicit
 * direct scope is left untouched (the mapper's
 * {@code preserveExplicitDirectScopes()} short-circuit), so
 * {@code @ConfigBean @RequestScoped MyConfig} stays {@code @RequestScoped}.
 * To disable the remap entirely, ship a higher-priority
 * {@code BeanScopeMapper}.
 */
@Target(METHOD)
@Retention(RUNTIME)
@Documented
public @interface TestControl {

    /**
     * Scope annotations to activate for this test method. When set to a
     * non-empty array, the {@code TestControlScopeObserver} vetoes the
     * {@code BeforeScopeStarted} event for every scope <em>not</em>
     * listed here, and the veto is honored:
     * {@code @TestMethodScoped} is fired per method by scope-module, so
     * omitting it leaves it inactive and its beans throw
     * {@code ContextNotActiveException} for that method.
     *
     * <p>Not affected by this attribute:
     * <ul>
     *   <li>{@code @TestClassScoped} — it has a <em>class</em> lifetime
     *       (activated once at {@code AfterBeanDiscovery}); no per-method
     *       {@code BeforeScopeStarted} is fired for it, so it cannot be
     *       suppressed per method regardless of whether it is listed;</li>
     *   <li>container-managed {@code @RequestScoped} and JPA-managed
     *       {@code @TransactionScoped} — never vetoed by this attribute.</li>
     * </ul>
     *
     * <p>The default empty array {@code {}} means
     * &quot;all scope-module scopes activate normally&quot; — no vetoing
     * is performed. This is the conventional &quot;allow everything&quot;
     * sentinel; an empty list would otherwise mean
     * &quot;allow nothing&quot; and break tests that do not use
     * {@code @TestControl} at all.
     *
     * @return the allow-list of scope annotation classes;
     *         empty means &quot;all scope-module scopes&quot;
     */
    Class<? extends Annotation>[] startScopes() default {};

    /**
     * Classpath test-data folders processed before the test method
     * runs and verified after it completes.
     *
     * <p>Each entry is either {@code "path/to/folder"} (uses the
     * default persistence unit) or {@code "puName:path/to/folder"}
     * (routes the {@code dbIn/} / {@code dbUpdate/} / {@code dbExpected/}
     * sub-folders to the named persistence unit). The folder may
     * contain any combination of these sub-directories:
     *
     * <ul>
     *   <li>{@code dbIn/*.xml} — clean-inserted via
     *       {@code DbSeed.forPersistenceUnit().dataset(xml).cleanInsert().execute()}.</li>
     *   <li>{@code dbUpdate/*.xml} — updated via
     *       {@code DbSeed.forPersistenceUnit().dataset(xml).update().execute()}.</li>
     *   <li>{@code dbExpected/*.xml} — asserted via
     *       {@code DbDiff.forPersistenceUnit().expected(xml).assertEquals()}
     *       after the test method (inside the {@code AfterTestTransaction}
     *       observer for transactional tests, or in testcontrol's
     *       {@code afterEach} otherwise).</li>
     * </ul>
     *
     * <p>Processing order: all {@code dbIn/} phases (across every
     * entry, in array order) complete before any {@code dbUpdate/}
     * phase starts; within each sub-directory the {@code *.xml} files
     * are processed in alphabetical order (natural {@code String} sort
     * of filenames). The {@code dbExpected/} verify phase runs after
     * the test method completes.
     *
     * <p>The default empty array {@code {}} disables both seeding and
     * verification — no folder scanning occurs.
     *
     * @return the test-data folder paths; empty means no fixture
     *         handling
     */
    String[] testData() default {};

    /**
     * Base path prepended to every {@link #testData()} entry. Useful
     * when an entire test class shares a common test-data root.
     *
     * <p>The default empty string means &quot;no prefix&quot; —
     * {@code testData} entries are resolved from the classpath root as
     * given.
     *
     * <p><b>Precedence.</b> The MicroProfile Config key
     * {@code org.os890.jawelte.module.testcontrol.api.TestControl.base-path}
     * (underscore variant
     * {@code org_os890_jawelte_module_testcontrol_api_TestControl_base-path})
     * overrides this attribute when set to a non-empty value.
     *
     * @return the prefix prepended to {@code testData} entries; empty
     *         means no prefix
     */
    String testDataBasePath() default "";

    /**
     * Whether at least one {@link #testData()} entry must supply at
     * least one {@code dbExpected/*.xml} dataset for the test method
     * to be considered correctly configured.
     *
     * <p>The default value {@code true} guards against a silent
     * regression where someone deletes (or empties out) the
     * {@code dbExpected/} folder of a verifying test — without the
     * guard, the test would still pass because the verify phase has
     * nothing to assert against. With the guard, the test fails fast
     * during {@code beforeEach} with a clear error pointing at the
     * missing assertion side.
     *
     * <p>Set to {@code false} on a per-method basis when the test
     * legitimately only seeds (e.g. a fixture-setup test that another
     * test consumes, or a smoke test that verifies via other means
     * than {@code DbDiff}).
     *
     * <p><b>Implicitly satisfied when {@code testData} is empty.</b>
     * If a test method uses {@code @TestControl} purely for other
     * features (e.g. {@code startScopes} only, or a future attribute
     * unrelated to seeding) and supplies no {@code testData} entries,
     * this attribute has no effect — the guard only runs when
     * {@code testData} is non-empty. The default {@code true} value
     * is therefore safe to leave in place on tests that never seed.
     *
     * <p>The guard fires when {@link #testData()} is non-empty and
     * NO entry contributes a {@code dbExpected/} sub-folder
     * containing at least one {@code *.xml} file. An entry with a
     * {@code dbExpected/} folder that has no XML files counts as
     * "no contribution" to keep the contract strong against the
     * "empty folder bypass" case.
     *
     * @return {@code true} (default) to require a non-empty
     *         {@code dbExpected/} contribution across the entries
     *         when {@code testData} is non-empty; {@code false} to
     *         opt out (seed-only path)
     */
    boolean requireDbExpected() default true;
}
