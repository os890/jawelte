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
package org.os890.jawelte.module.scope.api;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.context.NormalScope;

/**
 * Test-method-scoped CDI normal scope. Beans live for one
 * {@code @Test} method; their {@code @PreDestroy} runs at the end of
 * each {@code afterEach}.
 *
 * <p>Semantically follows {@code @ApplicationScoped} within a test
 * method: proxy-based, lazy first-access creation, a single managed
 * instance shared by every thread that dereferences the proxy during
 * that method. The difference versus {@code @ApplicationScoped} is the
 * lifetime — the instance is destroyed at the end of each test method
 * rather than at container shutdown.
 *
 * <p><strong>Activation.</strong> The context is active only while the
 * per-method store is allocated — between the lifecycle adapter's
 * {@code beforeEach} and {@code afterEach}. Dereferencing a
 * {@code @TestMethodScoped} bean outside an active method (e.g. from
 * {@code @BeforeAll}) or after its {@code BeforeScopeStarted} activation
 * was vetoed (e.g. via {@code @TestControl(startScopes=…)}) throws
 * {@code ContextNotActiveException}. Use {@code @TestClassScoped} for
 * fixtures that must span a test class's methods.
 *
 * <p><strong>Parallel test methods are not supported.</strong> There is
 * one store per CDI container (one per test class), shared across
 * threads, and the framework runs test methods sequentially. Under JUnit
 * {@code @Execution(CONCURRENT)} concurrent methods would share the same
 * bean instances, and one method's {@code afterEach} teardown could
 * destroy beans another method is still using. (testcontrol-module's
 * scope-filter state carries the same single-threaded assumption.)
 *
 * <p>{@code passivating = false} — the test framework does not
 * passivate beans; long-running serialised state is out of scope.
 */
@NormalScope(passivating = false)
@Inherited
@Target({TYPE, METHOD, FIELD})
@Retention(RUNTIME)
@Documented
public @interface TestMethodScoped {
}
