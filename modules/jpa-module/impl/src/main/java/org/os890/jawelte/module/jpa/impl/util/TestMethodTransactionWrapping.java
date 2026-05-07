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
package org.os890.jawelte.module.jpa.impl.util;

import java.lang.reflect.Method;
import java.util.Optional;

import org.os890.jawelte.core.api.port.TestContext;

/**
 * Reflective bridge into JUnit Jupiter's {@code ExtensionContext}
 * so {@code JpaLifecycleAdapter} can wrap a {@code @Transactional}
 * {@code @Test} method in a real transaction without taking a hard
 * compile-time dependency on {@code junit-jupiter-api} from
 * jpa-module's production code. The
 * {@code DelegatingJUnitExtension} binds the
 * {@code ExtensionContext} on the {@link TestContext}'s metadata
 * map under its own class key; this helper looks the binding up by
 * fully-qualified name and reflects on it.
 *
 * <p>If JUnit isn't on the classpath (or the metadata wasn't
 * bound), every helper returns {@link Optional#empty()} — the
 * lifecycle adapter then skips the wrap path entirely, keeping the
 * "no JUnit at runtime" surface working.
 */
public class TestMethodTransactionWrapping {

    private static final String EXTENSION_CONTEXT_FQCN = "org.junit.jupiter.api.extension.ExtensionContext";

    private TestMethodTransactionWrapping() {
    }

    /**
     * Read the current test method, if any, from the
     * {@code ExtensionContext} bound on {@code testContext}.
     *
     * @param testContext the framework's per-test context
     * @return the {@code @Test} method JUnit is currently dispatching;
     *         empty when JUnit isn't on the classpath, the
     *         {@code ExtensionContext} wasn't bound, or the
     *         {@code getTestMethod()} call returned empty
     */
    public static Optional<Method> currentTestMethod(TestContext testContext) {
        return readExtensionContextOptional(testContext, "getTestMethod", Method.class);
    }

    /**
     * Read the {@code Throwable} JUnit captured for the current
     * test method's body, if any.
     *
     * @param testContext the framework's per-test context
     * @return the test-method exception, if any; empty when the
     *         test passed or the {@code ExtensionContext} isn't on
     *         the classpath / wasn't bound
     */
    public static Optional<Throwable> currentExecutionException(TestContext testContext) {
        return readExtensionContextOptional(testContext, "getExecutionException", Throwable.class);
    }

    private static <T> Optional<T> readExtensionContextOptional(
            TestContext testContext, String accessor, Class<T> elementType) {
        Class<?> extensionContextClass;
        try {
            extensionContextClass = Class.forName(EXTENSION_CONTEXT_FQCN);
        } catch (ClassNotFoundException notOnClasspath) {
            return Optional.empty();
        }
        Object extensionContext = testContext.getMetadata(castClass(extensionContextClass)).orElse(null);
        if (extensionContext == null) {
            return Optional.empty();
        }
        try {
            // Invoke through the public ExtensionContext interface — JUnit's
            // concrete impl (MethodExtensionContext) is package-private and
            // rejects direct reflective access despite the method itself
            // being public on the interface.
            Object result = extensionContextClass.getMethod(accessor).invoke(extensionContext);
            if (result instanceof Optional<?> optional && optional.isPresent()
                    && elementType.isInstance(optional.get())) {
                return Optional.of(elementType.cast(optional.get()));
            }
        } catch (ReflectiveOperationException ignored) {
            // accessor missing or not callable on this JUnit version — treat as "no info".
        }
        return Optional.empty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Class castClass(Class<?> raw) {
        return raw;
    }
}
