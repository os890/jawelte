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
package org.os890.jawelte.tests.springdata.scenario08;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * User-owned {@code @Produces}-method covering
 * {@link CustomerRepository}. The bean returned here is a no-op
 * JDK proxy that records the method name of the most recent call on
 * a static field. The test invokes a known method and verifies the
 * recorder captured the call — proof that the user's producer (not
 * the extension's Spring Data synthetic) served the injection point.
 */
@ApplicationScoped
public class UserOwnedRepositoryProducer {

    /** Captures the name of the most recent method invoked on the produced repository. */
    public static final java.util.concurrent.atomic.AtomicReference<String> LAST_METHOD_CALLED =
            new java.util.concurrent.atomic.AtomicReference<>();

    /** No-arg constructor for CDI. */
    public UserOwnedRepositoryProducer() {
    }

    /**
     * The user-provided producer.
     *
     * @return a JDK proxy that records method-name calls into
     *         {@link #LAST_METHOD_CALLED} and returns null / 0 for
     *         every method, never touching the database
     */
    @Produces
    @ApplicationScoped
    public CustomerRepository produceCustomerRepository() {
        InvocationHandler handler = (proxy, method, args) -> {
            LAST_METHOD_CALLED.set(method.getName());
            Class<?> returnType = method.getReturnType();
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == double.class) {
                return 0.0d;
            }
            if (returnType == float.class) {
                return 0.0f;
            }
            return null;
        };
        return (CustomerRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{CustomerRepository.class},
                handler);
    }
}
