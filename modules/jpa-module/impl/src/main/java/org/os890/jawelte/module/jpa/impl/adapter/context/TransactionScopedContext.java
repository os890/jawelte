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
package org.os890.jawelte.module.jpa.impl.adapter.context;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.transaction.TransactionScoped;

/**
 * CDI {@link AlterableContext} for
 * {@link jakarta.transaction.TransactionScoped}. Driven exclusively
 * by jpa-module's {@code TransactionalInterceptor}: each
 * {@code @Transactional} invocation pushes a fresh frame on the
 * calling thread's deque, and the matching commit / rollback /
 * exception-finally pops + destroys it.
 *
 * <p>Frames stack so nested {@code @Transactional} invocations get
 * their own per-tx bean store — inner-transaction beans are
 * isolated from the outer's. {@link #isActive()} reports
 * {@code true} while the deque is non-empty on the calling thread.
 *
 * <p>{@link AlterableContext#destroy(Contextual)} removes a single
 * entry from the top frame so user code can force a
 * {@code @PreDestroy} mid-transaction (e.g. via
 * {@code Instance#destroy(Object)}).
 */
public class TransactionScopedContext implements AlterableContext {

    /**
     * Singleton handle on the most recently registered instance.
     * The CDI container wraps registered Contexts in its own internal
     * passivating-capable wrappers (e.g. OWB's
     * {@code CustomAlterablePassivatingContextImpl}); calling
     * {@code BeanManager.getContext(TransactionScoped.class)} returns
     * that wrapper, not our raw {@code TransactionScopedContext}, so
     * the interceptor cannot reach {@link #activate()} /
     * {@link #deactivate()} via the standard CDI API. Capturing
     * {@code this} on construction lets the interceptor look up the
     * concrete instance directly.
     */
    private static volatile TransactionScopedContext currentInstance;

    private final ThreadLocal<Deque<Map<Contextual<?>, TransactionScopedBeanInstance<?>>>> stacks =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * No-arg constructor used by {@code addContext}. Captures
     * {@code this} on the static {@link #currentInstance} handle so
     * {@link #current()} returns the active context regardless of
     * whether the CDI container wraps it.
     */
    public TransactionScopedContext() {
        currentInstance = this;
    }

    /**
     * The most recently constructed {@link TransactionScopedContext},
     * or {@code null} if no jpa-module CDI container has booted on
     * this JVM.
     *
     * @return the current context, or {@code null}
     */
    public static TransactionScopedContext current() {
        return currentInstance;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return TransactionScoped.class;
    }

    @Override
    public boolean isActive() {
        // Always-active Context (matches scope-module's pattern). The
        // CDI Container's beanManager.getContext(TransactionScoped.class)
        // is the only API that retrieves the Context object before any
        // tx scope has been activated; it requires isActive() == true to
        // succeed. The actual "is there a tx scope on this thread?"
        // guard happens at lookup time: get(Contextual, ...) calls
        // topOrThrow() which raises ContextNotActiveException when the
        // per-thread frame stack is empty.
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        Map<Contextual<?>, TransactionScopedBeanInstance<?>> frame = topOrThrow();
        TransactionScopedBeanInstance<?> existing = frame.get(contextual);
        if (existing != null) {
            return (T) existing.instance();
        }
        T instance = contextual.create(creationalContext);
        frame.put(contextual, new TransactionScopedBeanInstance<>(instance, creationalContext));
        return instance;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Contextual<T> contextual) {
        Map<Contextual<?>, TransactionScopedBeanInstance<?>> frame = stacks.get().peek();
        if (frame == null) {
            throw new ContextNotActiveException("@TransactionScoped is not active on the calling thread");
        }
        TransactionScopedBeanInstance<?> existing = frame.get(contextual);
        return existing == null ? null : (T) existing.instance();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void destroy(Contextual<?> contextual) {
        Map<Contextual<?>, TransactionScopedBeanInstance<?>> frame = stacks.get().peek();
        if (frame == null) {
            return;
        }
        TransactionScopedBeanInstance<?> removed = frame.remove(contextual);
        if (removed == null) {
            return;
        }
        Contextual rawContextual = contextual;
        rawContextual.destroy(removed.instance(), removed.creationalContext());
    }

    /**
     * Push a fresh empty bean-store frame onto the calling thread's
     * deque. Called by
     * {@code TransactionalInterceptor.aroundInvoke} immediately
     * after {@code TransactionStrategy.begin()} returns.
     */
    public void activate() {
        stacks.get().push(new HashMap<>());
    }

    /**
     * Pop the top frame on the calling thread's deque and
     * {@link Contextual#destroy(Object, CreationalContext)} every
     * entry. Called by
     * {@code TransactionalInterceptor.aroundInvoke} unconditionally
     * in its {@code finally} block. Per-bean failures aggregate per
     * the project exception policy (TICKET-001): the first failure
     * becomes the primary, subsequent failures are attached via
     * {@link Throwable#addSuppressed(Throwable)}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void deactivate() {
        Deque<Map<Contextual<?>, TransactionScopedBeanInstance<?>>> deque = stacks.get();
        if (deque.isEmpty()) {
            return;
        }
        Map<Contextual<?>, TransactionScopedBeanInstance<?>> frame = deque.pop();
        RuntimeException primary = null;
        for (Map.Entry<Contextual<?>, TransactionScopedBeanInstance<?>> entry : frame.entrySet()) {
            try {
                Contextual rawContextual = entry.getKey();
                rawContextual.destroy(entry.getValue().instance(), entry.getValue().creationalContext());
            } catch (RuntimeException destroyFailure) {
                if (primary == null) {
                    primary = destroyFailure;
                } else {
                    primary.addSuppressed(destroyFailure);
                }
            }
        }
        if (deque.isEmpty()) {
            stacks.remove();
        }
        if (primary != null) {
            throw primary;
        }
    }

    private Map<Contextual<?>, TransactionScopedBeanInstance<?>> topOrThrow() {
        Map<Contextual<?>, TransactionScopedBeanInstance<?>> frame = stacks.get().peek();
        if (frame == null) {
            throw new ContextNotActiveException("@TransactionScoped is not active on the calling thread");
        }
        return frame;
    }
}
