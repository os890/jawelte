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

import jakarta.annotation.Priority;
import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Scope;
import jakarta.transaction.Transactional;

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
 *       annotation class is reachable on the runtime classpath
 *       (resolved reflectively to avoid a compile-time dep on
 *       scope-module/api). Distinguished from
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
 * <p>The EJB-mapped scope is added only when the class does not
 * already carry a CDI scope (a normal-scope annotation or
 * {@code @Dependent}); when it does, the mapper skips the scope
 * addition because the class is bean-defining through its own
 * scope. The implicit {@code @Transactional} is added either way
 * — the scope decision and the transactional addition are
 * independent — unless the class already declares
 * {@code @jakarta.transaction.Transactional} itself, in which
 * case the author's {@code TxType} / {@code rollbackOn} /
 * {@code dontRollbackOn} attributes are kept and no second
 * {@code @Transactional} is added on top.
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
 * <p>The resolved scope class is read once at class-load time via
 * a reflective {@code Class.forName} of
 * {@code org.os890.jawelte.module.scope.api.TestClassScoped} — the
 * same pattern used by {@code WireMockRegistryScopeRemap}. When
 * scope-module isn't on the classpath the load returns
 * {@code null} and the mapper falls back to
 * {@code @ApplicationScoped}.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultEjbAnnotationMapper implements EjbAnnotationMapper {


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
    public List<Annotation> mapBeanMetadata(Class<?> beanClass, BeanManager beanManager) {
        boolean ejbSingleton = beanClass.isAnnotationPresent(Singleton.class);
        boolean ejbStateless = beanClass.isAnnotationPresent(Stateless.class);
        if (!ejbSingleton && !ejbStateless) {
            return null;
        }

        boolean userDeclaredScope = hasUserDeclaredCdiScope(beanClass);
        List<Annotation> additions = new ArrayList<>(2);

        // Scope: the EJB-mapped scope is added only when the class
        // has no CDI scope of its own. A class that already declares
        // a CDI scope is bean-defining through that scope; adding
        // the EJB-mapped scope on top would be redundant.
        if (!userDeclaredScope) {
            if (ejbSingleton) {
                additions.add(singletonScopeLiteral());
            } else {
                // @Stateless — always @Dependent. scope-module's
                // TestBeanDefaultScope is not consulted here on
                // purpose: a per-injection-point fresh instance does
                // not benefit from a long-lived test scope.
                additions.add(Dependent.Literal.INSTANCE);
            }
        }

        // @Transactional is added unconditionally for every class
        // the mapper claims, independent of the scope decision.
        // User-declared @Transactional precedence still applies:
        // a class that already carries @jakarta.transaction.Transactional
        // keeps the author's TxType / rollbackOn / dontRollbackOn
        // attributes — adding the default TxType.REQUIRED literal
        // on top would silently combine with the user's binding.
        if (!beanClass.isAnnotationPresent(Transactional.class)) {
            additions.add(TransactionalLiteral.INSTANCE);
        }
        return additions;
    }

    /**
     * Resolve the scope annotation literal to use for
     * {@code @Singleton} beans. Returns scope-module's
     * {@code @TestClassScoped} when its annotation class is
     * loadable on the runtime classpath (resolved once at
     * class-load time into {@link #TEST_CLASS_SCOPED}); otherwise
     * falls back to {@code @ApplicationScoped}.
     *
     * @return the annotation literal to apply to {@code @Singleton}
     *         beans
     */
    private static Annotation singletonScopeLiteral() {
        if (TEST_CLASS_SCOPED == null) {
            return ApplicationScoped.Literal.INSTANCE;
        }
        // scope-module's @TestClassScoped is not on the compile
        // classpath of ejb-module/impl; build the literal
        // reflectively from the class loaded above.
        return AnnotationInstanceFactory.create(TEST_CLASS_SCOPED);
    }

    private static final String TEST_CLASS_SCOPED_FQCN =
            "org.os890.jawelte.module.scope.api.TestClassScoped";

    private static final Class<? extends Annotation> TEST_CLASS_SCOPED = loadTestClassScoped();

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> loadTestClassScoped() {
        try {
            Class<?> loaded = Class.forName(
                    TEST_CLASS_SCOPED_FQCN,
                    true,
                    DefaultEjbAnnotationMapper.class.getClassLoader());
            if (!Annotation.class.isAssignableFrom(loaded)) {
                return null;
            }
            return (Class<? extends Annotation>) loaded;
        } catch (ClassNotFoundException | LinkageError missing) {
            return null;
        }
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
