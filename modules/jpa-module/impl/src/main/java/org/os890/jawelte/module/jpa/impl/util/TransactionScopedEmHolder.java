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
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.event.TransactionStarted;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;

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
     * Per-thread "a {@code @ReadOnly} scope is active" flag, set by
     * {@code ReadOnlyInterceptor} for the duration of the annotated
     * method's execution (its own transaction and everything called
     * below it). While it is {@code true}, {@link #peekOrAutoBegin(String)}
     * gives every newly created {@link EntityManager} the
     * {@link FlushModeType#COMMIT} flush mode, so a lazily-joined PU's
     * EM suppresses auto-flush exactly like the EMs the interceptor
     * swapped at entry. The interceptor restores flush modes on exit,
     * so an enclosing scope is never left read-only.
     */
    private static final ThreadLocal<Boolean> READ_ONLY_SCOPE =
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
     * Mark whether a {@code @ReadOnly} scope is active on the calling
     * thread. Called by {@code ReadOnlyInterceptor} — {@code true} for
     * the duration of the annotated method's execution, {@code false}
     * once it unwinds. While {@code true}, {@link #peekOrAutoBegin(String)}
     * creates read-only ({@link FlushModeType#COMMIT}) EntityManagers.
     *
     * @param active whether a {@code @ReadOnly} scope is active
     */
    public static void setReadOnlyScopeActive(boolean active) {
        READ_ONLY_SCOPE.set(active);
    }

    /**
     * Whether a {@code @ReadOnly} scope is currently active on the
     * calling thread (set by {@code ReadOnlyInterceptor}).
     *
     * @return {@code true} while a {@code @ReadOnly} method is executing
     *         on this thread
     */
    public static boolean isReadOnlyScopeActive() {
        return Boolean.TRUE.equals(READ_ONLY_SCOPE.get());
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
        // The internal frame storage is a LinkedHashSet so PUs are
        // tracked in join order. Set.copyOf would re-bucket entries
        // into a JDK ImmutableCollections.SetN whose iteration order
        // is hash-based — and therefore non-deterministic across runs.
        // Wrap in an unmodifiableSet view of a LinkedHashSet copy so
        // commit / rollback iterate in the join order the rest of the
        // module already documents.
        return Collections.unmodifiableSet(new LinkedHashSet<>(framesStack.peek()));
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
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        boolean jtaMode = strategy.getTransactionType() == PersistenceUnitTransactionType.JTA;
        // Source of truth for "is a tx active" differs between modes:
        // RESOURCE_LOCAL relies on the holder's own scope stack
        // (interceptor / strategy push frames there); JTA reads
        // strategy.isActive() because the JTA strategy doesn't push
        // onto the holder, and a programmatic userTx.begin() outside
        // the @Transactional interceptor reaches this method without
        // the holder's frame stack being populated.
        if (!jtaMode && !isTransactionalScopeActive()) {
            return null;
        }
        if (jtaMode && !strategy.isActive()) {
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
        if (isReadOnlyScopeActive()) {
            // A @ReadOnly scope is active on this thread: every EM created
            // within it — including this lazily-joined PU — must suppress
            // auto-flush, matching the EMs ReadOnlyInterceptor swapped at
            // entry. The interceptor restores the mode on exit, so an
            // enclosing scope is never left read-only.
            entityManager.setFlushMode(FlushModeType.COMMIT);
        }
        if (jtaMode) {
            // Under JTA the EM cannot drive its own EntityTransaction —
            // em.getTransaction() throws "JTA mode" — and a freshly
            // created EM is unsynchronized by default per JPA 3.2 §7.6.
            // joinTransaction() enlists the EM with the active JTA tx;
            // a Synchronization closes + pops the EM at tx complete so
            // jpa-module's per-PU stack stays in sync with the JTA
            // outcome the JTA strategy doesn't itself touch.
            entityManager.joinTransaction();
            registerJtaSynchronization(strategy, persistenceUnitName, entityManager);
        } else {
            entityManager.getTransaction().begin();
        }
        push(persistenceUnitName, entityManager);
        Deque<Set<String>> framesStack = FRAME_PUS_STACK.get();
        if (!framesStack.isEmpty()) {
            framesStack.peek().add(persistenceUnitName);
        }
        // Under RESOURCE_LOCAL the strategy fires TransactionStarted only
        // for the eagerly-managed PU; lazy-joining PUs fire their own
        // event from here. Under JTA the strategy fires exactly one
        // transaction-wide event per JTA tx (per ticket scenario 16),
        // so the holder must not fire a second per-PU event.
        if (!jtaMode) {
            fireTransactionStartedQuietly(persistenceUnitName);
        }
        return entityManager;
    }

    private static void registerJtaSynchronization(
            TransactionStrategy strategy, String persistenceUnitName, EntityManager entityManager) {
        TransactionManager transactionManager = strategy.getTransactionManager();
        if (transactionManager == null) {
            return;
        }
        try {
            Transaction transaction = transactionManager.getTransaction();
            if (transaction == null) {
                return;
            }
            transaction.registerSynchronization(new EmCleanupSynchronization(persistenceUnitName, entityManager));
            // Bind the strategy's lifecycle events for this tx if no
            // begin() on the strategy has done it already. Keeps
            // TransactionStarted / TransactionBeforeCompletion /
            // TransactionCommitted / TransactionRolledBack firing under
            // a vendor JTA CDI interceptor (Narayana, Quarkus) that
            // drives the tx via UserTransaction without going through
            // this strategy's begin / commit / rollback.
            strategy.bindLifecycleEventsToCurrentTransaction();
        } catch (RollbackException | SystemException registerFailure) {
            // The active tx is already finishing (or in error). Close the
            // EM ourselves so it doesn't leak; the strategy's commit /
            // rollback will fail on this anyway and surface the underlying
            // tx error.
            try {
                entityManager.close();
            } catch (RuntimeException ignored) {
                // best-effort — primary failure is the tx-state one
            }
        }
    }

    /**
     * {@link Synchronization} that pops the EM from the per-PU stack
     * and closes it once the active JTA transaction completes. The
     * jpa-module RESOURCE_LOCAL strategy handles pop+close itself; the
     * JTA strategy does not, so the lazy-begin path under JTA carries
     * its own cleanup.
     */
    private static class EmCleanupSynchronization implements Synchronization {

        private final String persistenceUnitName;

        private final EntityManager entityManager;

        EmCleanupSynchronization(String persistenceUnitName, EntityManager entityManager) {
            this.persistenceUnitName = persistenceUnitName;
            this.entityManager = entityManager;
        }

        @Override
        public void beforeCompletion() {
            // No-op — Hibernate's JTA coordinator runs its own
            // beforeCompletion to flush the EM before commit.
        }

        @Override
        public void afterCompletion(int status) {
            try {
                Deque<EntityManager> stack = STACKS.get().get(persistenceUnitName);
                if (stack != null && stack.peek() == entityManager) {
                    stack.pop();
                    if (stack.isEmpty()) {
                        STACKS.get().remove(persistenceUnitName);
                    }
                }
            } finally {
                try {
                    if (entityManager.isOpen()) {
                        entityManager.close();
                    }
                } catch (RuntimeException ignored) {
                    // best-effort — JTA tx is already finished
                }
            }
            // Status hint: STATUS_COMMITTED vs STATUS_ROLLED_BACK does not
            // change cleanup behaviour here, so the value is unused.
            int unusedStatusHint = status;
            // referencing the parameter so checkstyle / IDE warnings
            // don't fire on the deliberately-unused argument.
            if (unusedStatusHint == Status.STATUS_UNKNOWN) {
                // never reached for well-behaved JTA TMs
            }
        }
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
        READ_ONLY_SCOPE.remove();
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
