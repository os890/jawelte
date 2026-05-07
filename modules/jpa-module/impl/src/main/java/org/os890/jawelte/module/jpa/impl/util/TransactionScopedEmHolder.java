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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

/**
 * Per-thread stack of active {@link EntityManager}s, keyed by
 * persistence unit name. Each entry of the per-thread map is a
 * {@link Deque} (used as a LIFO stack) so that nested
 * {@code @Transactional} invocations on the same thread push fresh
 * managers without losing the outer manager.
 *
 * <p>The {@code EntityManagerProxy} injected at every IP delegates
 * every method call to the result of {@link #peek(String)} for its
 * persistence unit. The active {@link
 * org.os890.jawelte.module.jpa.api.port.TransactionStrategy} pushes
 * a fresh {@code EntityManager} on {@code begin()} and pops on
 * {@code commit()} or {@code rollback()}.
 *
 * <p>{@link #clearForCurrentThread()} removes the entire thread-local
 * map; jpa-module's lifecycle adapter calls it from
 * {@code afterEach} as a safety net so a stray test does not leak
 * EM state across methods.
 */
public abstract class TransactionScopedEmHolder {

    private static final ThreadLocal<Map<String, Deque<EntityManager>>> STACKS = ThreadLocal.withInitial(HashMap::new);

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected TransactionScopedEmHolder() {
    }

    /**
     * Push a fresh {@link EntityManager} onto the calling thread's
     * stack for the given persistence unit.
     *
     * @param persistenceUnitName the persistence unit name
     * @param entityManager       the manager to push
     */
    public static void push(String persistenceUnitName, EntityManager entityManager) {
        STACKS.get().computeIfAbsent(persistenceUnitName, n -> new ArrayDeque<>()).push(entityManager);
    }

    /**
     * Pop and return the top {@link EntityManager} of the calling
     * thread's stack for the given persistence unit.
     *
     * @param persistenceUnitName the persistence unit name
     * @return the popped manager
     * @throws IllegalStateException if the stack for this persistence
     *                               unit is empty on the calling
     *                               thread
     */
    public static EntityManager pop(String persistenceUnitName) {
        Deque<EntityManager> stack = STACKS.get().get(persistenceUnitName);
        if (stack == null || stack.isEmpty()) {
            throw new IllegalStateException(
                    "No EntityManager on stack for persistence unit '" + persistenceUnitName + "'.");
        }
        return stack.pop();
    }

    /**
     * Return — without removing — the top {@link EntityManager} of
     * the calling thread's stack for the given persistence unit.
     *
     * @param persistenceUnitName the persistence unit name
     * @return the top manager, or {@code null} if the stack is empty
     */
    public static EntityManager peek(String persistenceUnitName) {
        Deque<EntityManager> stack = STACKS.get().get(persistenceUnitName);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    /**
     * Whether the calling thread has any active {@link EntityManager}
     * across all persistence units.
     *
     * @return {@code true} if every per-PU stack is empty,
     *         {@code false} otherwise
     */
    public static boolean isEmpty() {
        for (Deque<EntityManager> stack : STACKS.get().values()) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Clear the entire thread-local map for the calling thread. The
     * next access lazily re-initialises an empty map.
     *
     * <p>Called by {@code JpaLifecycleAdapter.afterEach} as a safety
     * net so a stray test method that pushed but never popped does
     * not leak {@link EntityManager} state across methods.
     */
    public static void clearForCurrentThread() {
        STACKS.remove();
    }
}
