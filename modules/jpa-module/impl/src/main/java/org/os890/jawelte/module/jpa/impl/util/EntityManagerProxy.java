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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import jakarta.persistence.EntityManager;

/**
 * JDK-dynamic-proxy {@link InvocationHandler} for
 * {@link EntityManager}. Every method call delegates to the top of
 * the calling thread's stack via
 * {@link TransactionScopedEmHolder#peek(String)}. If the stack is
 * empty, the handler raises {@link IllegalStateException} with a
 * message pointing the user at the missing transaction boundary.
 *
 * <p>Use {@link #create(String)} to obtain an {@code EntityManager}
 * proxy for the named persistence unit. One proxy instance is
 * registered per persistence unit (per qualifier set) by
 * {@code JpaCdiExtension.afterBeanDiscovery}; the proxy itself is
 * stateless, so a single instance can serve every IP across the
 * lifetime of the CDI container.
 *
 * <p>{@link Object#equals(Object)}, {@link Object#hashCode()}, and
 * {@link Object#toString()} are handled locally — the proxy uses
 * its persistence unit name as the basis so that two proxies for
 * the same persistence unit compare equal regardless of stack
 * state.
 */
public class EntityManagerProxy implements InvocationHandler {

    private final String persistenceUnitName;

    /**
     * Construct a handler for the given persistence unit. Use
     * {@link #create(String)} to obtain an {@link EntityManager}
     * proxy backed by this handler.
     *
     * @param persistenceUnitName the persistence unit name to delegate
     *                            for
     */
    public EntityManagerProxy(String persistenceUnitName) {
        this.persistenceUnitName = persistenceUnitName;
    }

    /**
     * Build a JDK proxy implementing {@link EntityManager} that
     * delegates every method to the top of the calling thread's
     * stack for the named persistence unit.
     *
     * @param persistenceUnitName the persistence unit name
     * @return the proxy
     */
    public static EntityManager create(String persistenceUnitName) {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManagerProxy.class.getClassLoader(),
                new Class<?>[] {EntityManager.class},
                new EntityManagerProxy(persistenceUnitName));
    }

    /**
     * Get the persistence unit name this proxy delegates for.
     *
     * @return the persistence unit name
     */
    public String getPersistenceUnitName() {
        return persistenceUnitName;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }
        EntityManager active = TransactionScopedEmHolder.peek(persistenceUnitName);
        if (active == null) {
            throw new IllegalStateException(
                    "No active EntityManager for persistence unit '" + persistenceUnitName
                            + "'. Was the call made outside a @Transactional or "
                            + "UserTransaction.begin() boundary?");
        }
        try {
            return method.invoke(active, args);
        } catch (InvocationTargetException invocation) {
            throw invocation.getCause();
        }
    }

    private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> args != null && args.length == 1 && proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "EntityManagerProxy[" + persistenceUnitName + "]";
            default -> throw new UnsupportedOperationException(
                    "Object method not handled by EntityManagerProxy: " + method.getName());
        };
    }
}
