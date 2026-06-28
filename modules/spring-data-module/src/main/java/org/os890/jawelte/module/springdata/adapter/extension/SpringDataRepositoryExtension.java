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
package org.os890.jawelte.module.springdata.adapter.extension;

import java.lang.System.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.os890.jawelte.core.api.port.TestContext;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * CDI {@link Extension} that auto-discovers Spring Data
 * {@link Repository}-extending interfaces during CDI bootstrap and
 * registers a real Spring Data implementation per interface as a
 * {@link RequestScoped @RequestScoped} CDI bean.
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li><b>{@code ProcessInjectionPoint}</b> — for every injection
 *       point in the deployment, the extension inspects the IP's
 *       declared type. If the type is an interface that transitively
 *       extends {@link Repository} and is not annotated
 *       {@link NoRepositoryBean @NoRepositoryBean}, the interface is
 *       added to the discovery set. {@code ProcessInjectionPoint}
 *       (not {@code ProcessAnnotatedType}) is the right callback
 *       here because repository interfaces are typically not in a
 *       bean archive under {@code bean-discovery-mode="annotated"} —
 *       {@code ProcessAnnotatedType} never fires for them, but their
 *       use as an injection-point type is observable.</li>
 *   <li><b>{@code ProcessBean}</b> — accumulates the types of every
 *       bean processed by the container so the
 *       {@code AfterBeanDiscovery} pass can detect interfaces that
 *       already have a producer (back-off: a user
 *       {@code @Produces CustomerRepository} wins over the
 *       extension's synthetic bean).</li>
 *   <li><b>{@code AfterBeanDiscovery}</b> — for each discovered
 *       repository interface that has no existing bean, the
 *       extension registers a synthetic {@code @RequestScoped} bean.
 *       The bean's {@code produceWith} callback (invoked lazily on
 *       first method call through the CDI client proxy) resolves
 *       the {@link EntityManager} from CDI, constructs a
 *       {@link JpaRepositoryFactory}, and returns
 *       {@code factory.getRepository(repositoryInterface)} — the
 *       same real implementation Spring Data builds in production.</li>
 * </ol>
 *
 * <h2>Bean types and qualifiers</h2>
 *
 * <p>The registered bean's types are exactly the discovered
 * repository interface itself plus {@link Object} — framework
 * super-interfaces ({@code JpaRepository}, {@code CrudRepository},
 * &hellip;) are deliberately NOT added as bean types so the
 * synthetic beans never compete for resolution on framework
 * super-types when multiple repositories ship together. Qualifiers
 * are {@link Default @Default} and {@link Any @Any}; no
 * {@code @Named} is added.
 *
 * <h2>Why {@code @RequestScoped}</h2>
 *
 * <p>The synthetic bean is a normal-scoped CDI bean, so consumers
 * receive a CDI client proxy at every injection point and the
 * underlying {@link JpaRepositoryFactory} is materialised lazily on
 * the first method invocation — which always happens inside a
 * {@code @Transactional} or programmatic-transaction boundary,
 * where the EM proxy supplied by jpa-module/impl can resolve a
 * live {@link EntityManager}. {@code @RequestScoped} (rather than
 * {@code @ApplicationScoped}) limits the factory's lifetime to a
 * single test method: cdi-module's {@code CdiTestBeanContainer}
 * activates a {@code RequestContextController} per test method, so
 * each test gets a fresh factory and any (theoretical) Spring Data
 * metadata-cache mutation cannot leak between tests. The
 * {@link jakarta.persistence.EntityManagerFactory} that Spring Data
 * resolves through the injected {@code EntityManager} is itself a
 * CDI bean — its scope and caching are the EMF producer's choice,
 * not this extension's. If jpa-module is on the classpath it ships
 * a JVM-cached default producer; otherwise the consumer must define
 * their own.
 *
 * <h2>EntityManager resolution</h2>
 *
 * <p>The {@code produceWith} callback resolves the
 * {@code @Default} {@link EntityManager} via
 * {@code CDI.current().select(EntityManager.class).get()} — the
 * per-call routing proxy registered by jpa-module/impl. Repository
 * methods therefore participate in the caller's
 * {@code @Transactional} boundary automatically. Multi-persistence-unit
 * routing is not supported (the default EM is always used); consumers
 * with multiple PUs must supply their own
 * {@code @Produces CustomerRepository} per repository.
 *
 * <p>Discovered via
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}.
 */
public class SpringDataRepositoryExtension implements Extension {

    private static final Logger LOGGER = System.getLogger(SpringDataRepositoryExtension.class.getName());

    private static final String SPRING_DATA_PACKAGE_PREFIX = "org.springframework.data";

    // Concurrent: Weld dispatches ProcessInjectionPoint / ProcessBean events
    // on multiple (ForkJoinPool) threads, so these sets are mutated from
    // several threads during the deployment lifecycle phase.
    private final Set<Class<?>> discoveredRepositories = ConcurrentHashMap.newKeySet();

    private final Set<Type> existingBeanTypes = ConcurrentHashMap.newKeySet();

    /** No-arg constructor required by the CDI runtime. */
    public SpringDataRepositoryExtension() {
    }

    /**
     * Inspect every injection point's declared type. When the type
     * is a repository interface (extends {@link Repository}
     * transitively, not annotated {@link NoRepositoryBean}, not a
     * Spring Data marker interface), it is queued for synthetic-bean
     * registration in {@link #onAfterBeanDiscovery(AfterBeanDiscovery)}.
     *
     * @param <T> the declared type of the injection point
     * @param <X> the bean class that declares the injection point
     * @param event the CDI {@code ProcessInjectionPoint} event
     */
    <T, X> void onProcessInjectionPoint(@Observes ProcessInjectionPoint<T, X> event) {
        Type ipType = event.getInjectionPoint().getType();
        if (!(ipType instanceof Class<?> rawType)) {
            return;
        }
        if (!rawType.isInterface()) {
            return;
        }
        if (isSpringDataMarker(rawType)) {
            return;
        }
        if (rawType.isAnnotationPresent(NoRepositoryBean.class)) {
            return;
        }
        if (!Repository.class.isAssignableFrom(rawType)) {
            return;
        }
        if (discoveredRepositories.add(rawType)) {
            LOGGER.log(Logger.Level.INFO,
                    "Discovered Spring Data repository interface for auto-registration: " + rawType.getName());
        }
    }

    /**
     * Accumulate the types contributed by every bean the container
     * processes. The set powers the back-off check in
     * {@link #onAfterBeanDiscovery(AfterBeanDiscovery)} — when a user
     * {@code @Produces}-method (or any other CDI bean) already
     * contributes a repository interface as a bean type, the
     * extension declines to register its own synthetic bean for that
     * interface.
     *
     * @param <X> the bean class being processed
     * @param event the CDI {@code ProcessBean} event
     */
    <X> void onProcessBean(@Observes ProcessBean<X> event) {
        existingBeanTypes.addAll(event.getBean().getTypes());
    }

    /**
     * Register one synthetic {@code @RequestScoped} CDI bean per
     * discovered repository interface that is not already covered by
     * an existing bean. The {@code produceWith} callback resolves the
     * {@code @Default} {@link EntityManager} via {@code CDI.current()}
     * lazily on the first method call through the CDI client proxy
     * and builds the repository through Spring Data's
     * {@link JpaRepositoryFactory}.
     *
     * @param event the CDI {@code AfterBeanDiscovery} event
     */
    void onAfterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        collectFromTestClass();
        for (Class<?> repositoryInterface : discoveredRepositories) {
            if (existingBeanTypes.contains(repositoryInterface)) {
                LOGGER.log(Logger.Level.INFO,
                        "Skipping synthetic bean for " + repositoryInterface.getName()
                                + " — an existing bean already covers this type");
                continue;
            }
            event.addBean()
                    .beanClass(repositoryInterface)
                    .types(repositoryInterface, Object.class)
                    .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .scope(RequestScoped.class)
                    .produceWith(ctx -> buildRepository(repositoryInterface));
            LOGGER.log(Logger.Level.INFO,
                    "Registered synthetic @RequestScoped CDI bean for repository interface "
                            + repositoryInterface.getName());
        }
    }

    private void collectFromTestClass() {
        TestContext testContext;
        try {
            testContext = TestContext.get();
        } catch (IllegalStateException notInBootstrap) {
            return;
        }
        for (Class<?> current = testContext.getTestClass();
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (!fieldType.isInterface()) {
                    continue;
                }
                if (isSpringDataMarker(fieldType)) {
                    continue;
                }
                if (fieldType.isAnnotationPresent(NoRepositoryBean.class)) {
                    continue;
                }
                if (!Repository.class.isAssignableFrom(fieldType)) {
                    continue;
                }
                if (discoveredRepositories.add(fieldType)) {
                    LOGGER.log(Logger.Level.INFO,
                            "Discovered Spring Data repository interface from test class field: "
                                    + fieldType.getName());
                }
            }
        }
    }

    private static Object buildRepository(Class<?> repositoryInterface) {
        EntityManager entityManager = CDI.current().select(EntityManager.class).get();
        JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
        return factory.getRepository(repositoryInterface);
    }

    private static boolean isSpringDataMarker(Class<?> candidate) {
        String packageName = candidate.getPackageName();
        return packageName.equals(SPRING_DATA_PACKAGE_PREFIX)
                || packageName.startsWith(SPRING_DATA_PACKAGE_PREFIX + ".");
    }
}
