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
 * <p>Semantically follows {@code @ApplicationScoped}: proxy-based,
 * lazy first-access creation, a single managed instance shared by
 * every thread that dereferences the proxy. The only difference
 * versus {@code @ApplicationScoped} is the bean instance lifetime —
 * destroyed per test method instead of at container shutdown. Bean
 * access never throws {@code ContextNotActiveException}: the context
 * reports {@code isActive() == true} for the whole CDI container's
 * lifetime, lazily allocating a fresh store between methods.
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
