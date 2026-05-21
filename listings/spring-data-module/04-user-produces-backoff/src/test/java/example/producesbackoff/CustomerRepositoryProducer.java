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
package example.producesbackoff;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * User-supplied @Produces method. spring-data-module's CDI extension
 * observes this bean during ProcessBean, sees that CustomerRepository
 * already has a producer, and skips its own synthetic registration.
 *
 * <p>The returned object is a JDK proxy that records every method
 * call on a static field — proof during the test that this producer
 * (not the synthetic) served the injection.
 */
@ApplicationScoped
public class CustomerRepositoryProducer {

    public static final AtomicReference<String> LAST_METHOD_CALLED = new AtomicReference<>();

    @Produces
    @ApplicationScoped
    CustomerRepository produce() {
        InvocationHandler handler = (proxy, method, args) -> {
            LAST_METHOD_CALLED.set(method.getName());
            Class<?> returnType = method.getReturnType();
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == boolean.class) {
                return false;
            }
            return null;
        };
        return (CustomerRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {CustomerRepository.class},
                handler);
    }
}
