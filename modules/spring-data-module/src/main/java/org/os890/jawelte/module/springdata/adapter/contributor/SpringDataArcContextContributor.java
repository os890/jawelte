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
package org.os890.jawelte.module.springdata.adapter.contributor;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.impl.adapter.quarkus.JaweltAutoMockBuildCompatibleExtension;
import org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.processor.BeanProcessor;

/**
 * spring-data-module's {@link ArcContextContributor}: replaces the
 * {@code AfterBeanDiscovery} half of
 * {@code SpringDataRepositoryExtension}, which calls
 * {@code event.addBean()...} — ArC returns {@code null} from that
 * builder, so synthetic-bean registration for every discovered
 * Spring Data repository interface never happens under the ArC-based
 * test container.
 *
 * <p>This contributor performs the equivalent registration via ArC's
 * {@code BeanRegistrar} surface. For every {@code @Inject}-able
 * Spring Data repository interface found on the active test class
 * (and not annotated {@code @NoRepositoryBean}), it registers a
 * synthetic {@code @RequestScoped} CDI bean whose creator builds the
 * repository through Spring Data's {@code JpaRepositoryFactory} on
 * first use.
 *
 * <p>The matching bean shape is also pre-registered through
 * {@link JaweltAutoMockBuildCompatibleExtension#preRegisterExistingBeanShape}
 * so the auto-mock BCE and {@code MockAndInlineBeanRegistrar} don't
 * add a competing auto-mock for the same IPs.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor}.
 */
public class SpringDataArcContextContributor implements ArcContextContributor {

    private static final String SPRING_DATA_PACKAGE_PREFIX = "org.springframework.data";

    private static final String REPOSITORY_INTERFACE_PARAM = "repositoryInterface";

    /** No-arg constructor required by {@code ServiceLoader}. */
    public SpringDataArcContextContributor() {
    }

    @Override
    public void contribute(TestContext testContext, BeanProcessor.Builder builder) {
        Set<Class<?>> repositoryInterfaces = discoverRepositoryInterfaces(testContext.getTestClass());
        if (repositoryInterfaces.isEmpty()) {
            return;
        }
        builder.addBeanRegistrar(registration -> {
            for (Class<?> repositoryInterface : repositoryInterfaces) {
                DotName typeDot = DotName.createSimple(repositoryInterface.getName());
                // If a user-supplied bean (e.g. an @Produces method)
                // already covers this repository interface, back off:
                // the original extension does the same through its
                // ProcessBean / existingBeanTypes observer chain.
                // Registering a competing synthetic here would land us
                // in AmbiguousResolutionException at deployment.
                if (!registration.beans().withBeanType(typeDot).collect().isEmpty()) {
                    continue;
                }
                @SuppressWarnings({"rawtypes", "unchecked"})
                io.quarkus.arc.processor.BeanConfigurator<?> configurator =
                        registration.configure(typeDot);
                configurator.scope(RequestScoped.class)
                        .addType(repositoryInterface)
                        .addQualifier(AnnotationInstance.builder(
                                DotName.createSimple("jakarta.enterprise.inject.Default")).build())
                        .creator((Class) RepositoryCreator.class)
                        .param(REPOSITORY_INTERFACE_PARAM, repositoryInterface.getName());
                configurator.done();
            }
        });

        // Pre-register the shape so the BCE auto-mock and
        // MockAndInlineBeanRegistrar don't add a parallel synthetic
        // bean for these IPs. Repository IPs are unqualified — the
        // pre-registered shape uses an empty qualifier set, matching
        // qualifierFqnSet's filtering of @Default / @Any / @Named.
        for (Class<?> repositoryInterface : repositoryInterfaces) {
            JaweltAutoMockBuildCompatibleExtension.preRegisterExistingBeanShape(
                    repositoryInterface.getName(), Set.of());
        }
    }

    /**
     * Finds every {@code @Inject}-able Spring Data repository
     * interface reachable from any class on the test classpath.
     * The original {@code SpringDataRepositoryExtension} sees those
     * IPs through {@code ProcessInjectionPoint}, which fires for
     * every IP in the bean archive — mirrors that scope here by
     * scanning {@code target/test-classes} (and {@code target/classes})
     * directly, since standalone-ArC doesn't dispatch the portable
     * extension's PIP observer.
     */
    private static Set<Class<?>> discoverRepositoryInterfaces(Class<?> testClass) {
        Set<Class<?>> result = new LinkedHashSet<>();
        Set<Class<?>> visited = new LinkedHashSet<>();
        // First, the test class tree (covers nested @ApplicationScoped
        // bridge beans like Scenario02Test.CrudInvoker).
        collectRepositoriesFromClassTree(testClass, result, visited);
        // Then every other class in target/test-classes — picks up
        // top-level helper beans (Scenario07Test's CustomerService)
        // that aren't reachable through the test class's nesting.
        for (Class<?> candidate : scanTestClassesDirectory(testClass)) {
            collectRepositoriesFromClassTree(candidate, result, visited);
        }
        return result;
    }

    private static void collectRepositoriesFromClassTree(
            Class<?> entry, Set<Class<?>> result, Set<Class<?>> visited) {
        for (Class<?> current = entry; current != null && current != Object.class;
                current = current.getSuperclass()) {
            if (!visited.add(current)) {
                return;
            }
            collectRepositoriesFromDeclaredFields(current, result);
            for (Class<?> nested : current.getDeclaredClasses()) {
                collectRepositoriesFromClassTree(nested, result, visited);
            }
        }
    }

    private static void collectRepositoriesFromDeclaredFields(Class<?> owner, Set<Class<?>> result) {
        for (Field field : owner.getDeclaredFields()) {
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
            result.add(fieldType);
        }
    }

    /**
     * Walks {@code target/test-classes} (and {@code target/classes})
     * under the test class's project directory and returns every
     * class loadable through the test class's ClassLoader. Same
     * approach {@code CdiTestBeanContainer.discoverBeanClasses}
     * uses; replicated here to keep spring-data-module independent
     * of cdi-module/impl's internal API.
     */
    private static Set<Class<?>> scanTestClassesDirectory(Class<?> testClass) {
        Set<Class<?>> result = new LinkedHashSet<>();
        ClassLoader cl = testClass.getClassLoader();
        if (cl == null) {
            return result;
        }
        try {
            String resourceName = testClass.getName().replace('.', '/') + ".class";
            java.net.URL url = cl.getResource(resourceName);
            if (url == null) {
                return result;
            }
            String path = url.getFile();
            int idx = path.indexOf("target/test-classes");
            if (idx < 0) {
                return result;
            }
            String projectBase = path.substring(0, idx);
            java.io.File testClassesDir = new java.io.File(projectBase + "target/test-classes");
            if (testClassesDir.isDirectory()) {
                scanDirectory(testClassesDir, testClassesDir, cl, result);
            }
            java.io.File classesDir = new java.io.File(projectBase + "target/classes");
            if (classesDir.isDirectory()) {
                scanDirectory(classesDir, classesDir, cl, result);
            }
        } catch (RuntimeException scanFailure) {
            // best-effort discovery — any IP we miss surfaces later as
            // an unsatisfied resolution at deployment time
        }
        return result;
    }

    private static void scanDirectory(
            java.io.File root, java.io.File dir, ClassLoader cl, Set<Class<?>> result) {
        java.io.File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        for (java.io.File entry : entries) {
            if (entry.isDirectory()) {
                scanDirectory(root, entry, cl, result);
                continue;
            }
            if (!entry.getName().endsWith(".class")) {
                continue;
            }
            String relative = entry.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
            String className = relative.substring(0, relative.length() - ".class".length())
                    .replace(java.io.File.separatorChar, '.');
            try {
                Class<?> loaded = Class.forName(className, false, cl);
                result.add(loaded);
            } catch (Throwable loadFailure) {
                // skip classes that fail to load (e.g. missing deps);
                // anything we couldn't reach can't have repository IPs
                // we care about
            }
        }
    }

    private static boolean isSpringDataMarker(Class<?> candidate) {
        String packageName = candidate.getPackageName();
        return packageName.equals(SPRING_DATA_PACKAGE_PREFIX)
                || packageName.startsWith(SPRING_DATA_PACKAGE_PREFIX + ".");
    }

    /** Creator for the synthetic Spring Data repository bean. */
    public static class RepositoryCreator implements BeanCreator<Object> {

        /** No-arg constructor required by ArC's reflective lookup. */
        public RepositoryCreator() {
        }

        @Override
        public Object create(SyntheticCreationalContext<Object> ctx) {
            String repositoryFqn = (String) ctx.getParams().get(REPOSITORY_INTERFACE_PARAM);
            if (repositoryFqn == null) {
                throw new IllegalStateException(
                        "Spring Data synthetic bean is missing the '" + REPOSITORY_INTERFACE_PARAM
                                + "' param");
            }
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = SpringDataArcContextContributor.class.getClassLoader();
            }
            Class<?> repositoryInterface;
            try {
                repositoryInterface = Class.forName(repositoryFqn, false, cl);
            } catch (ClassNotFoundException notFound) {
                throw new IllegalStateException(
                        "Cannot load Spring Data repository interface " + repositoryFqn, notFound);
            }
            EntityManager entityManager = CDI.current().select(EntityManager.class).get();
            JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
            return factory.getRepository(repositoryInterface);
        }
    }
}
