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
package org.os890.jawelte.module.ejb.impl;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Scope;
import jakarta.transaction.Transactional;

import org.os890.jawelte.core.api.port.ScopeBinding;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper;

/**
 * Terminal {@link EjbAnnotationMapper}. Maps the two standard
 * session-bean annotations to CDI scopes plus an implicit
 * class-level {@code @jakarta.transaction.Transactional}:
 *
 * <ul>
 *   <li>{@code @jakarta.ejb.Singleton} — defaults to
 *       {@code @ApplicationScoped}; promoted to
 *       {@code @TestClassScoped} when scope-module's
 *       {@link ScopeBinding.TestBeanDefaultScope} record is bound on
 *       {@link TestContext}. Distinguished from
 *       {@code jakarta.inject.Singleton} by direct
 *       {@code Class<? extends Annotation>} identity — full class
 *       name distinction by string is wrong and must not be
 *       reintroduced.</li>
 *   <li>{@code @jakarta.ejb.Stateless} — always
 *       {@code @Dependent}. Per-injection-point fresh instance
 *       matches EJB's stateless contract from the consumer's
 *       perspective; long-lived test scopes are not applied (a
 *       stateless bean does not benefit).</li>
 * </ul>
 *
 * <p>A user-declared CDI scope on the type wins over both the
 * EJB-mapped scope and the {@code TestBeanDefaultScope} override.
 * The {@code @Transactional} addition is independent of the scope
 * decision — every EJB-mapped bean gets it.
 *
 * <p>The other class-level EJB annotations
 * ({@code @Stateful}, {@code @MessageDriven}, {@code @Lock},
 * {@code @AccessTimeout}, {@code @Startup}, {@code @DependsOn},
 * {@code @Schedule}, {@code @Asynchronous},
 * {@code @TransactionAttribute}) are silently ignored — this
 * mapper returns {@code null} for any class that carries neither
 * {@code @Singleton} nor {@code @Stateless}, so the chain finishes
 * without claiming the class and no annotations are added. A user
 * who needs different behaviour ships a custom additional
 * {@link EjbAnnotationMapper} and registers it via
 * {@code ServiceLoader}.
 *
 * <p>The resolved scope class is read lazily from
 * {@code TestContext.getMetadata(ScopeBinding.TestBeanDefaultScope.class)}
 * on first {@code @Singleton} encounter and cached for the rest of
 * the bootstrap; the metadata is bound during
 * {@code BeforeBeanDiscovery} and never changes after.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultEjbAnnotationMapper implements EjbAnnotationMapper {

    /** Cached singleton-scope class, resolved lazily on first {@code @Singleton} encounter. */
    private volatile Class<? extends Annotation> cachedSingletonScope;

    /**
     * Required public no-arg constructor for
     * {@code ServiceLoader} instantiation.
     */
    public DefaultEjbAnnotationMapper() {
    }

    @Override
    public boolean isAdditionalMapper() {
        return false;
    }

    @Override
    public Set<Class<? extends Annotation>> observedAnnotations() {
        // The default mapper only acts on the two standard EJB
        // session-bean annotations; declaring them here lets the CDI
        // Extension restrict its fast-path PAT observer via
        // @WithAnnotations.
        return Set.of(Singleton.class, Stateless.class);
    }

    @Override
    public List<Annotation> mapBeanMetadata(Class<?> beanClass, BeanManager beanManager) {
        boolean ejbSingleton = beanClass.isAnnotationPresent(Singleton.class);
        boolean ejbStateless = beanClass.isAnnotationPresent(Stateless.class);
        if (!ejbSingleton && !ejbStateless) {
            return null;
        }

        boolean userDeclaredScope = hasUserDeclaredCdiScope(beanClass);
        boolean userDeclaredTransactional = beanClass.isAnnotationPresent(Transactional.class);
        List<Annotation> additions = new ArrayList<>(2);

        if (!userDeclaredScope) {
            if (ejbSingleton) {
                additions.add(singletonScopeLiteral());
            } else {
                // @Stateless — always @Dependent, scope-module is
                // ignored here on purpose (a per-injection-point
                // fresh instance does not benefit from a long-lived
                // test scope).
                additions.add(Dependent.Literal.INSTANCE);
            }
        }

        // User-declared-wins precedence applies to @Transactional too:
        // a class that already carries @jakarta.transaction.Transactional
        // (with any TxType / rollbackOn / dontRollbackOn the author
        // chose) keeps those attributes — adding the default
        // TxType.REQUIRED literal on top would silently combine with
        // the user's binding in an implementation-defined way.
        if (!userDeclaredTransactional) {
            additions.add(TransactionalLiteral.INSTANCE);
        }
        return additions;
    }

    /**
     * Resolve the scope annotation to use for {@code @Singleton}
     * beans. Reads
     * {@code ScopeBinding.TestBeanDefaultScope} from
     * {@link TestContext} on the first call and caches the result;
     * subsequent calls return the cached value.
     */
    private Annotation singletonScopeLiteral() {
        Class<? extends Annotation> resolved = cachedSingletonScope;
        if (resolved == null) {
            resolved = resolveSingletonScope();
            cachedSingletonScope = resolved;
        }
        if (resolved == ApplicationScoped.class) {
            return ApplicationScoped.Literal.INSTANCE;
        }
        // For any other scope (notably scope-module's
        // @TestClassScoped, which we do not have on the compile
        // classpath), build the annotation instance reflectively.
        return AnnotationInstanceFactory.create(resolved);
    }

    private Class<? extends Annotation> resolveSingletonScope() {
        Optional<ScopeBinding.TestBeanDefaultScope> bound;
        try {
            bound = TestContext.get().getMetadata(ScopeBinding.TestBeanDefaultScope.class);
        } catch (IllegalStateException notInBootstrap) {
            // No active TestContext (out-of-band test usage). Fall
            // back to @ApplicationScoped — the original baseline.
            return ApplicationScoped.class;
        }
        return bound
                .map(ScopeBinding.TestBeanDefaultScope::scope)
                .orElse(ApplicationScoped.class);
    }

    /**
     * Whether {@code beanClass} already carries a CDI scope
     * annotation declared by the user. A CDI scope is any annotation
     * meta-annotated with {@link NormalScope} or {@link Scope} — same
     * detection cdi-module uses for {@code @TestBean} static-field
     * scope inference. Single pass over the class's annotations; no
     * annotation hierarchy walk.
     */
    private static boolean hasUserDeclaredCdiScope(Class<?> beanClass) {
        for (Annotation annotation : beanClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType.isAnnotationPresent(NormalScope.class)
                    || annotationType.isAnnotationPresent(Scope.class)) {
                return true;
            }
        }
        return false;
    }
}
