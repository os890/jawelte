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
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

import org.os890.jawelte.core.api.port.ServicePriorityResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper;

/**
 * CDI {@link Extension} that drives the {@link EjbAnnotationMapper}
 * chain — the runtime piece of ejb-module.
 *
 * <p>During {@code BeforeBeanDiscovery} the extension:
 * <ul>
 *   <li>Enumerates the available {@link EjbAnnotationMapper} candidates
 *       via {@code ServiceLoader.load(EjbAnnotationMapper.class)} and
 *       sorts them by passing the list to
 *       {@code TestContext.loadService(ServicePriorityResolver.class).sort(...)}.
 *       This is the same precedent {@code JtaTransactionStrategy} uses
 *       for the {@code TransactionManagerProvider} chain.
 *       {@code TestContext.loadService(EjbAnnotationMapper.class)} is
 *       not used here because it returns only the head of the sorted
 *       list, and the mapper chain genuinely needs every candidate in
 *       order.</li>
 *   <li>Splits the sorted chain into the additional mappers
 *       ({@code isAdditionalMapper() == true}, run first) and the
 *       single terminal default ({@code isAdditionalMapper() == false},
 *       run when every additional mapper returned {@code null}).</li>
 *   <li>Calls
 *       {@link BeforeBeanDiscovery#addStereotype(Class, Annotation...)}
 *       to register {@code @jakarta.ejb.Singleton} and
 *       {@code @jakarta.ejb.Stateless} as CDI stereotypes so they
 *       become bean-defining under
 *       {@code bean-discovery-mode="annotated"}. The stereotype's
 *       implied scope is the EJB baseline (Singleton →
 *       {@code @ApplicationScoped}, Stateless → {@code @Dependent});
 *       the mapper chain at {@code ProcessAnnotatedType} time may
 *       override it with an explicit scope on the AnnotatedType,
 *       which wins by CDI precedence.</li>
 * </ul>
 *
 * <p>During {@code ProcessAnnotatedType<T>} the extension walks the
 * sorted chain in priority order: the first additional mapper that
 * returns a non-{@code null} result claims the class; the terminal
 * default runs only when every additional mapper returned
 * {@code null}. Non-empty results are applied via
 * {@code configureAnnotatedType().add(...)}.
 *
 * <p>The extension is registered through
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * so the CDI runtime picks it up via the standard service-loader
 * lookup at container start.
 */
public class EjbAnnotationExtension implements Extension {

    /** Additional mappers in priority order, populated on {@code BeforeBeanDiscovery}. */
    private final List<EjbAnnotationMapper> additionalMappers = new ArrayList<>();

    /** Terminal default mapper, populated on {@code BeforeBeanDiscovery}. */
    private EjbAnnotationMapper terminalMapper;

    /**
     * Required public no-arg constructor for CDI Extension
     * {@code ServiceLoader} instantiation.
     */
    public EjbAnnotationExtension() {
    }

    /**
     * Resolve the mapper chain and register the EJB session-bean
     * annotations as CDI stereotypes.
     *
     * @param event the in-flight {@code BeforeBeanDiscovery} event
     *              the CDI runtime delivers; non-{@code null}
     */
    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
        resolveMapperChain();
        event.addStereotype(Singleton.class,
                ApplicationScoped.Literal.INSTANCE,
                TransactionalLiteral.INSTANCE);
        event.addStereotype(Stateless.class,
                Dependent.Literal.INSTANCE,
                TransactionalLiteral.INSTANCE);
    }

    /**
     * Run the mapper chain against the type being processed and
     * apply the resulting annotations via
     * {@link AnnotatedTypeConfigurator#add(Annotation)}.
     *
     * @param event the in-flight {@code ProcessAnnotatedType} event
     *              the CDI runtime delivers; non-{@code null}
     * @param <T>   the annotated type's bean class
     */
    <T> void onProcessAnnotatedType(@Observes ProcessAnnotatedType<T> event, BeanManager beanManager) {
        AnnotatedType<T> annotatedType = event.getAnnotatedType();
        Class<T> beanClass = annotatedType.getJavaClass();

        List<Annotation> result = invokeChain(beanClass, beanManager);
        if (result == null || result.isEmpty()) {
            return;
        }
        AnnotatedTypeConfigurator<T> configurator = event.configureAnnotatedType();
        for (Annotation annotation : result) {
            configurator.add(annotation);
        }
    }

    private List<Annotation> invokeChain(Class<?> beanClass, BeanManager beanManager) {
        for (EjbAnnotationMapper mapper : additionalMappers) {
            List<Annotation> result = mapper.mapBeanMetadata(beanClass, beanManager);
            if (result != null) {
                return result;
            }
        }
        if (terminalMapper != null) {
            return terminalMapper.mapBeanMetadata(beanClass, beanManager);
        }
        return null;
    }

    private void resolveMapperChain() {
        List<EjbAnnotationMapper> candidates = new ArrayList<>();
        for (EjbAnnotationMapper mapper : ServiceLoader.load(EjbAnnotationMapper.class)) {
            candidates.add(mapper);
        }
        if (candidates.isEmpty()) {
            // Hypothetical classpath with ejb-module/api but no
            // ejb-module/impl. invokeChain returns null for every
            // type; the stereotype declarations alone keep the
            // bean-defining behaviour, with the EJB baseline scopes
            // inherited from the stereotypes.
            return;
        }
        List<EjbAnnotationMapper> sorted = TestContext
                .loadService(ServicePriorityResolver.class)
                .sort(candidates);
        Iterator<EjbAnnotationMapper> iterator = sorted.iterator();
        while (iterator.hasNext()) {
            EjbAnnotationMapper mapper = iterator.next();
            if (mapper.isAdditionalMapper()) {
                additionalMappers.add(mapper);
            } else if (terminalMapper == null) {
                terminalMapper = mapper;
            }
            // If a second terminal mapper appears, the
            // priority-sorted-first one already won; the rest are
            // ignored. Per the chain contract there should be
            // exactly one terminal default on the classpath.
        }
    }
}
