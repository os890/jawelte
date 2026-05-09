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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.os890.jawelte.module.jpa.api.event.TransactionStarted;

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
     * Per-thread stack of "managed" persistence-unit names — the PU
     * the active {@link
     * org.os890.jawelte.module.jpa.api.port.TransactionStrategy} drove
     * the begin on for the current frame. One entry per nested
     * {@code @Transactional} level. Empty when no transactional
     * scope is active on the calling thread.
     */
    private static final ThreadLocal<Deque<String>> MANAGED_PU_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Per-thread stack of "PU names that joined this frame's tx
     * scope" — the managed PU plus any non-managed PUs that
     * lazy-began via {@link #peekOrAutoBegin(String)}. Walked by the
     * strategy on commit/rollback to complete every PU's tx, in
     * reverse-of-join order. One entry per nested
     * {@code @Transactional} level.
     */
    private static final ThreadLocal<Deque<Set<String>>> FRAME_PUS_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Per-thread "framework owns this tx" flag. Set whenever the
     * active strategy drives a {@code begin()} (directly or via
     * {@link #peekOrAutoBegin(String)}'s lazy-join path), cleared
     * whenever the matching {@code commit()} / {@code rollback()}
     * finishes. The four CDI tx events
     * ({@code TransactionStarted} / {@code BeforeCompletion} /
     * {@code Committed} / {@code RolledBack}) only fire while this
     * flag is {@code true} — user code that calls
     * {@code em.getTransaction().begin()} directly bypasses the
     * framework and therefore does not trigger the events.
     */
    private static final ThreadLocal<Boolean> FRAMEWORK_OWNED =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

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
     * Mark the start of a transactional scope on the calling
     * thread. The given persistence-unit name is recorded as the
     * "managed" PU for the new frame; any subsequent
     * {@link #peekOrAutoBegin(String)} calls for non-managed PUs
     * will lazy-create an {@link EntityManager} and begin a tx on
     * first access.
     *
     * <p>Pairs with {@link #exitTransactionalScope()}; one
     * enter/exit per {@code @Transactional} nesting level.
     *
     * @param managedPersistenceUnitName the PU the strategy drove
     *                                   the {@code begin()} on
     */
    public static void enterTransactionalScope(String managedPersistenceUnitName) {
        MANAGED_PU_STACK.get().push(managedPersistenceUnitName);
        Set<String> framePersistenceUnits = new LinkedHashSet<>();
        framePersistenceUnits.add(managedPersistenceUnitName);
        FRAME_PUS_STACK.get().push(framePersistenceUnits);
        FRAMEWORK_OWNED.set(Boolean.TRUE);
    }

    /**
     * Mark the start of an "all-lazy" transactional scope on the
     * calling thread — no eagerly-opened persistence unit. The first
     * {@link #peekOrAutoBegin(String)} call for any active PU
     * lazy-creates its {@link EntityManager} and begins a tx on
     * first access; subsequent calls for other PUs lazy-join the
     * same frame.
     *
     * <p>Used by the strategy when no persistence unit can be
     * eagerly identified: multi-PU active, no
     * {@code @PersistenceConfig.persistenceUnitName} set, and the
     * field-walk found either zero or multiple distinct
     * {@code @Named EntityManager} candidates.
     *
     * <p>Pairs with {@link #exitTransactionalScope()} like the
     * single-arg overload. The empty-string sentinel pushed onto
     * {@code MANAGED_PU_STACK} keeps the stack-depth bookkeeping
     * consistent with the eager path; nothing reads its value.
     */
    public static void enterTransactionalScope() {
        MANAGED_PU_STACK.get().push("");
        FRAME_PUS_STACK.get().push(new LinkedHashSet<>());
        FRAMEWORK_OWNED.set(Boolean.TRUE);
    }

    /**
     * Mark the end of the current transactional scope on the
     * calling thread. Pops one entry from each stack; if the stacks
     * are empty after popping, the thread-locals are removed.
     */
    public static void exitTransactionalScope() {
        Deque<Set<String>> framesStack = FRAME_PUS_STACK.get();
        if (!framesStack.isEmpty()) {
            framesStack.pop();
        }
        if (framesStack.isEmpty()) {
            FRAME_PUS_STACK.remove();
        }
        Deque<String> managedStack = MANAGED_PU_STACK.get();
        if (!managedStack.isEmpty()) {
            managedStack.pop();
        }
        if (managedStack.isEmpty()) {
            MANAGED_PU_STACK.remove();
            FRAMEWORK_OWNED.remove();
        }
    }

    /**
     * Whether the current transactional scope on the calling
     * thread was opened by the framework (the strategy or the
     * lazy-join path) rather than by user code calling
     * {@code em.getTransaction().begin()} directly. Read by the
     * event-firing helpers to gate their fire calls.
     *
     * @return {@code true} if the framework drives the active scope
     *         on this thread; {@code false} otherwise
     */
    public static boolean isFrameworkOwned() {
        return Boolean.TRUE.equals(FRAMEWORK_OWNED.get());
    }

    /**
     * Whether a transactional scope is active on the calling
     * thread.
     *
     * @return {@code true} if at least one frame has been entered
     *         and not yet exited; {@code false} otherwise
     */
    public static boolean isTransactionalScopeActive() {
        return !MANAGED_PU_STACK.get().isEmpty();
    }

    /**
     * The persistence-unit names that have joined the current
     * frame's transactional scope on the calling thread — the
     * managed PU plus any non-managed PUs that lazy-began. Order
     * preserved (insertion order = join order).
     *
     * @return the joined PUs for the current frame, or an empty set
     *         if no scope is active
     */
    public static Set<String> currentFramePersistenceUnits() {
        Deque<Set<String>> framesStack = FRAME_PUS_STACK.get();
        if (framesStack.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(framesStack.peek());
    }

    /**
     * Return the top {@link EntityManager} for the given persistence
     * unit, lazy-creating it (and beginning a transaction) when
     * none exists and a transactional scope is active. The new EM
     * is pushed onto the per-PU stack and the PU is added to the
     * current frame's joined set; the
     * {@link TransactionStarted} event is fired for the PU.
     *
     * <p>When no transactional scope is active on the calling
     * thread, behaves like {@link #peek(String)} — returns the
     * top EM if any, or {@code null} when the stack is empty.
     *
     * @param persistenceUnitName the persistence unit name
     * @return the top (existing or newly auto-begun)
     *         {@link EntityManager}, or {@code null} when no scope
     *         is active and the stack is empty
     */
    public static EntityManager peekOrAutoBegin(String persistenceUnitName) {
        EntityManager existing = peek(persistenceUnitName);
        if (existing != null) {
            return existing;
        }
        if (!isTransactionalScopeActive()) {
            return null;
        }
        if (!JpaActivePersistenceUnits.get().contains(persistenceUnitName)) {
            return null;
        }
        EntityManagerFactory factory = EmfCache.getCached(persistenceUnitName).orElse(null);
        if (factory == null) {
            return null;
        }
        EntityManager entityManager = factory.createEntityManager();
        entityManager.getTransaction().begin();
        push(persistenceUnitName, entityManager);
        Deque<Set<String>> framesStack = FRAME_PUS_STACK.get();
        if (!framesStack.isEmpty()) {
            framesStack.peek().add(persistenceUnitName);
        }
        fireTransactionStartedQuietly(persistenceUnitName);
        return entityManager;
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
        MANAGED_PU_STACK.remove();
        FRAME_PUS_STACK.remove();
        FRAMEWORK_OWNED.remove();
    }

    private static void fireTransactionStartedQuietly(String persistenceUnitName) {
        if (!isFrameworkOwned()) {
            return;
        }
        try {
            CDI.current().getBeanManager().getEvent().fire(new TransactionStarted(persistenceUnitName));
        } catch (RuntimeException ignored) {
            // CDI not up, observer threw, etc. — events are best-effort;
            // observer failures are aggregated by the framework.
        }
    }
}
