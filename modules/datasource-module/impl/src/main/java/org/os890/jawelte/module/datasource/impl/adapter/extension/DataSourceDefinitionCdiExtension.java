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
package org.os890.jawelte.module.datasource.impl.adapter.extension;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import jakarta.annotation.Priority;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.annotation.sql.DataSourceDefinitions;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.interceptor.Interceptor;

import org.os890.jawelte.core.api.SuppliedTypeRegistry;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.datasource.api.port.DataSourceFactory;
import org.os890.jawelte.module.datasource.impl.DataSourceLifecycle;
import org.os890.jawelte.module.datasource.impl.adapter.jndi.DataSourceJndiBinder;

/**
 * CDI extension shipped by datasource-module/impl. Owns two
 * responsibilities:
 *
 * <ol>
 *   <li><b>Discovery</b> — collects every
 *       {@link DataSourceDefinition} (and every member of a
 *       {@link DataSourceDefinitions} container) declared on the test
 *       class hierarchy or on any bean type in the archive. The
 *       resulting {@code (name -> definition)} map drives both the
 *       synthetic-bean registration and the build below.</li>
 *   <li><b>Synthetic-bean registration</b> — during
 *       {@code AfterBeanDiscovery}, one {@code @Dependent}
 *       {@link DataSource} bean per definition, qualified
 *       {@code @Named(<the definition's name>)} so
 *       {@code @Inject @Named("java:comp/env/jdbc/OrdersDS") DataSource}
 *       resolves. Each {@code produceWith} hands back the instance built
 *       below, so injection and a JNDI lookup of the same name yield
 *       the identical object rather than two separately-built
 *       ones.</li>
 *   <li><b>Construction</b> — during
 *       {@code AfterDeploymentValidation}, which is what makes a
 *       declared data source usable from a startup observer; see
 *       {@link #onAfterDeploymentValidation}.</li>
 * </ol>
 *
 * <p><b>Sole definition also gets {@code @Default}</b>, so the common
 * single-data-source test can write a plain {@code @Inject DataSource}.
 * With two or more definitions no bean is {@code @Default}: an
 * unqualified injection point is genuinely ambiguous then, and the
 * standard CDI {@code UnsatisfiedResolutionException} at deployment
 * time says so more clearly than an arbitrary winner would.
 *
 * <p><b>Inert when unused.</b> With no {@code @DataSourceDefinition}
 * anywhere the map stays empty, no bean is registered, and the
 * extension has done nothing observable — the module on the classpath
 * changes nothing by itself. The {@code @WithAnnotations} filter means
 * the container only calls this extension for types that actually
 * carry one of the two annotations, so even the scan costs nothing for
 * everybody else.
 */
public class DataSourceDefinitionCdiExtension implements Extension {

    /**
     * Definitions discovered so far, keyed by declared name.
     *
     * <p>Concurrent because {@link #onProcessAnnotatedType} is one of the
     * observers Weld invokes in parallel — the same hazard #69 found in
     * cdi/batch/wiremock. A plain map would lose definitions under a
     * racing put, and {@code putIfAbsent} on it would not be the atomic
     * check the duplicate-name detection below assumes.
     *
     * <p>No insertion order is kept, because under parallel discovery
     * there is none to keep. Where a stable order matters it is imposed
     * by sorting on the name, which is deterministic.
     */
    private final ConcurrentMap<String, DataSourceDefinition> definitionsByName =
            new ConcurrentHashMap<>();

    /**
     * What {@link #onAfterDeploymentValidation} built, keyed by declared
     * name.
     *
     * <p>Concurrent from the other direction: the write happens once
     * during deployment, but the reads happen whenever an injection
     * point is resolved — including from a thread a test spawned itself,
     * which the tree does do (see {@code tests/scope-module} scenario
     * 09).
     */
    private final ConcurrentMap<String, DataSource> builtByName = new ConcurrentHashMap<>();

    /** No-arg constructor required by the CDI runtime. */
    public DataSourceDefinitionCdiExtension() {
    }

    /**
     * Read the definitions declared on the test class hierarchy.
     *
     * <p>Done explicitly rather than left to
     * {@link #onProcessAnnotatedType} because a test class is not
     * necessarily a discovered bean type, and the test class is the
     * place a definition is most likely to sit.
     *
     * @param event the CDI lifecycle event
     */
    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        TestContext context;
        try {
            context = TestContext.get();
        } catch (IllegalStateException notInBootstrap) {
            // Not a jawelte bootstrap (or outside the bootstrap
            // window) — there is no test class to read, and the
            // ProcessAnnotatedType path still covers bean types.
            return;
        }
        for (Class<?> current = context.getTestClass();
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            collectFrom(current);
        }
    }

    /**
     * Read the definitions declared on a discovered bean type.
     *
     * @param event the annotated type, pre-filtered by the container
     *              to the two annotations of interest
     */
    void onProcessAnnotatedType(
            @Observes @WithAnnotations({DataSourceDefinition.class, DataSourceDefinitions.class})
            ProcessAnnotatedType<?> event) {
        collectFrom(event.getAnnotatedType().getJavaClass());
    }

    private void collectFrom(Class<?> candidate) {
        DataSourceDefinitions container = candidate.getAnnotation(DataSourceDefinitions.class);
        if (container != null) {
            for (DataSourceDefinition definition : container.value()) {
                collect(definition, candidate);
            }
        }
        DataSourceDefinition single = candidate.getAnnotation(DataSourceDefinition.class);
        if (single != null) {
            collect(single, candidate);
        }
    }

    private void collect(DataSourceDefinition definition, Class<?> declaringClass) {
        String name = definition.name();
        if (name == null || name.isBlank()) {
            throw new IllegalStateException(
                    "@DataSourceDefinition on " + declaringClass.getName()
                            + " declares no name — the name is what it is bound and injected under.");
        }
        DataSourceDefinition previous = definitionsByName.putIfAbsent(name, definition);
        if (previous != null && !previous.equals(definition)) {
            throw new IllegalStateException(
                    "Two different @DataSourceDefinition declarations use the name '" + name
                            + "'. The second one was found on " + declaringClass.getName()
                            + ". A name identifies one data source.");
        }
    }

    /**
     * Register one {@link DataSource} bean per discovered definition.
     *
     * @param event the CDI lifecycle event
     */
    void onAfterBeanDiscovery(
            @Observes @Priority(Interceptor.Priority.LIBRARY_BEFORE) AfterBeanDiscovery event) {
        if (definitionsByName.isEmpty()) {
            return;
        }
        // Recorded here, next to the registration it describes, so
        // cdi-module's auto-mocking does not stand in for a type this
        // module supplies itself. Its observer runs after this one.
        SuppliedTypeRegistry.active().markSupplied(DataSource.class);
        boolean soleDefinition = definitionsByName.size() == 1;
        for (String name : sortedNames()) {
            var beanBuilder = event.addBean()
                    .types(DataSource.class, Object.class)
                    .scope(Dependent.class)
                    .produceWith(instance -> dataSourceFor(name));
            if (soleDefinition) {
                beanBuilder.qualifiers(NamedLiteral.of(name), Default.Literal.INSTANCE, Any.Literal.INSTANCE);
            } else {
                beanBuilder.qualifiers(NamedLiteral.of(name), Any.Literal.INSTANCE);
            }
        }
    }

    /**
     * Build every declared data source and bind it, while the container
     * is still deploying.
     *
     * <p><b>Why here and not in the lifecycle adapter.</b> A
     * {@code TestModuleLifecyclePort} runs after
     * {@code TestBeanContainerPort.beforeAll}, which is what starts the
     * container — so by the time such an adapter could build anything,
     * {@code @Initialized(ApplicationScoped.class)} has already been
     * fired and any startup observer using a declared data source has
     * already failed. Schema migration, readiness probes and cache
     * warm-up all live in that window, and it is exactly the code a
     * test most wants to cover.
     *
     * <p>{@code AfterDeploymentValidation} is the last deployment event
     * and fires before the application context starts, so building here
     * puts the data sources in place before any application bean can
     * observe startup — which is the order a real container establishes
     * a {@code @DataSourceDefinition} in.
     *
     * <p>A failure releases whatever was already built and is reported
     * as a deployment problem, so the container refuses to start rather
     * than coming up with half the declarations honoured.
     *
     * @param event the CDI lifecycle event
     */
    // Ordered before jpa-module's AfterDeploymentValidation observer: a
    // persistence unit naming a declared data source has to find it
    // already built (#123).
    void onAfterDeploymentValidation(
            @Observes @Priority(Interceptor.Priority.LIBRARY_BEFORE) AfterDeploymentValidation event) {
        if (definitionsByName.isEmpty()) {
            return;
        }
        try {
            DataSourceFactory factory = resolveFactory();
            for (String name : sortedNames()) {
                DataSource dataSource = factory.create(definitionsByName.get(name));
                if (dataSource == null) {
                    throw new IllegalStateException(
                            factory.getClass().getName()
                                    + " returned null for @DataSourceDefinition(name = \"" + name + "\")");
                }
                builtByName.put(name, dataSource);
                DataSourceJndiBinder.bind(name, dataSource);
            }
        } catch (RuntimeException buildFailure) {
            releaseAll(buildFailure);
            event.addDeploymentProblem(buildFailure);
        }
    }

    /**
     * Unbind and close everything built for this container.
     *
     * <p>Called from the lifecycle adapter's {@code afterAll}, and from
     * this class when a build fails part-way — the container never
     * starts on that path, so nothing else would release what was
     * already opened.
     *
     * @param collectTo failures are attached here as suppressed rather
     *                  than thrown, so cleanup cannot mask the outcome
     *                  that caused it
     */
    public void releaseAll(Throwable collectTo) {
        for (Map.Entry<String, DataSource> entry : builtByName.entrySet()) {
            try {
                DataSourceJndiBinder.unbind(entry.getKey());
                DataSourceLifecycle.closeIfCloseable(entry.getValue());
            } catch (RuntimeException releaseFailure) {
                collectTo.addSuppressed(releaseFailure);
            }
        }
        builtByName.clear();
    }

    /**
     * The data source built for a declared name.
     *
     * @param name the {@code @DataSourceDefinition} name
     * @return the built data source
     * @throws IllegalStateException when nothing was built under that
     *         name — which means the definition was discovered but the
     *         deployment-time build did not run or did not reach it
     */
    public DataSource dataSourceFor(String name) {
        DataSource dataSource = builtByName.get(name);
        if (dataSource == null) {
            throw new IllegalStateException(
                    "No DataSource built for '" + name + "'. Built names: "
                            + new TreeMap<>(builtByName).keySet()
                            + ". The definition was discovered, so the deployment-time build either "
                            + "did not run or failed before reaching it.");
        }
        return dataSource;
    }

    /**
     * Whether anything was built for this container.
     *
     * @return {@code true} when there is something to release
     */
    public boolean hasBuiltDataSources() {
        return !builtByName.isEmpty();
    }

    /**
     * Declared names in a deterministic order.
     *
     * <p>Both the bean registration and the build iterate this rather
     * than the map directly: parallel discovery gives no meaningful
     * insertion order, so sorting is what makes a deployment reproduce
     * the same way twice.
     *
     * @return the declared names, sorted
     */
    private List<String> sortedNames() {
        return definitionsByName.keySet().stream().sorted().toList();
    }

    private static DataSourceFactory resolveFactory() {
        DataSourceFactory factory = TestContext.loadService(DataSourceFactory.class);
        if (factory == null) {
            throw new IllegalStateException(
                    "No " + DataSourceFactory.class.getName() + " on the classpath. "
                            + "jawelte-datasource-module-impl ships the default one; a consumer replacing it "
                            + "must keep exactly one registered via META-INF/services.");
        }
        return factory;
    }

    /**
     * The definitions discovered for the active test class, keyed by
     * declared name.
     *
     * @return an immutable snapshot
     */
    public Map<String, DataSourceDefinition> discoveredDefinitions() {
        return Map.copyOf(definitionsByName);
    }
}
