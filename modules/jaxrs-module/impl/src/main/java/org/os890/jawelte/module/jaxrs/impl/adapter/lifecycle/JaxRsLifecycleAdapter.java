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
package org.os890.jawelte.module.jaxrs.impl.adapter.lifecycle;

import java.lang.System.Logger;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.RuntimeDelegate;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.impl.TestUrlHolder;
import org.os890.jawelte.module.jaxrs.impl.adapter.filter.CdiIntegrationFilter;

/**
 * {@link TestModuleLifecyclePort} adapter shipped by
 * jaxrs-module/impl. Boots an embedded Jakarta REST 4.0
 * {@code SeBootstrap} server in {@code beforeAll} on port {@code 0}
 * (OS-assigned), publishes the resolved base URL on
 * {@link TestUrlHolder}, and shuts the server down in
 * {@code afterAll}.
 *
 * <p><b>Priority.</b> {@code @Priority(75)} — between
 * testcontrol-module (50) and scope-module (100). Server is ready
 * by the time scopes activate (and any JPA-related work at 200
 * begins); torn down in LIFO order after scopes deactivate but
 * before cdi-module closes the {@code SeContainer}.
 *
 * <p><b>Trigger.</b> The adapter is a no-op for test classes that
 * are not annotated {@link EnableJaxRs}. When present, the adapter
 * boots {@code SeBootstrap}. The companion {@code @EnableTestBeans}
 * is brought in transparently because {@code @EnableJaxRs} is
 * meta-annotated with it — the adapter doesn't need to validate
 * its presence (by the time {@code beforeAll} reaches this
 * adapter, jawelte's machinery is by definition active).
 *
 * <p><b>Provider probe.</b> Before calling {@code SeBootstrap.start},
 * {@link RuntimeDelegate#getInstance()} is invoked as a probe. If
 * it raises a {@link RuntimeException} or {@link LinkageError}
 * (typical when no JAX-RS implementation is on the classpath), the
 * adapter raises
 * {@code IllegalStateException("No JAX-RS SeBootstrap implementation found")}
 * with the probe's failure attached as cause.
 *
 * <p><b>Application.</b> A minimal {@link Application} subclass is
 * constructed with the union of the user-supplied
 * {@link EnableJaxRs#restResources()} and
 * {@link CdiIntegrationFilter}. The filter activates the CDI
 * request scope per HTTP request so resource beans can inject
 * {@code @RequestScoped} dependencies.
 *
 * <p><b>Resolved URL.</b> After {@code SeBootstrap.start} returns
 * (within {@value #SERVER_START_TIMEOUT_SECONDS} seconds), the
 * resolved port is read off {@code instance.configuration()
 * .baseUri()} and a {@code "http://localhost:{port}"} URL is
 * published on the {@link TestUrlHolder} bean. The host is
 * always {@code "localhost"} regardless of how the provider's
 * default bind address resolved — matching the documented
 * {@link org.os890.jawelte.module.jaxrs.api.TestUrl#get()}
 * contract.
 *
 * <p><b>Cleanup.</b> {@code afterAll} reads the
 * {@link SeBootstrap.Instance} back off
 * {@link TestContext#getMetadata(Class)}, calls {@code stop()},
 * waits up to {@value #SERVER_STOP_TIMEOUT_SECONDS} seconds for
 * draining, and then clears the {@link TestUrlHolder}. Stop
 * failures are logged at WARNING and swallowed (per TICKET-011
 * cleanup contract); the metadata is always unbound in
 * {@code finally}.
 *
 * <p><b>Start failure recovery.</b> If anything between a
 * successful {@code start()} and the metadata binding raises, the
 * partially-started server is stopped before the exception
 * propagates so the OS port doesn't leak.
 *
 * <p><b>State.</b> Stateless — no instance fields. The
 * {@link SeBootstrap.Instance} reference lives on {@link TestContext}
 * under {@code SeBootstrap.Instance.class} key for the lifetime of
 * the test class.
 */
@Priority(75)
public class JaxRsLifecycleAdapter implements TestModuleLifecyclePort {

    /**
     * Upper bound on how long {@code beforeAll} waits for
     * {@code SeBootstrap.start} to complete. Beyond this an
     * {@link IllegalStateException} is raised and the test class is
     * failed in {@code beforeAll}.
     */
    static final long SERVER_START_TIMEOUT_SECONDS = 30L;

    /**
     * Upper bound on how long {@code afterAll} waits for
     * {@code SeBootstrap.Instance.stop} to complete. Stop failures
     * (including timeouts) are logged and swallowed.
     */
    static final long SERVER_STOP_TIMEOUT_SECONDS = 10L;

    private static final Logger LOGGER = System.getLogger(JaxRsLifecycleAdapter.class.getName());

    /** No-arg constructor used by {@code ServiceLoader}. */
    public JaxRsLifecycleAdapter() {
    }

    @Override
    public void beforeAll(TestContext testContext) {
        Class<?> testClass = testContext.getTestClass();
        EnableJaxRs annotation = testClass.getAnnotation(EnableJaxRs.class);
        if (annotation == null) {
            return;
        }
        probeJaxRsRuntime();

        Set<Class<?>> classes = new LinkedHashSet<>();
        classes.add(CdiIntegrationFilter.class);

        Set<Object> singletons = new LinkedHashSet<>();
        for (Class<?> resourceClass : annotation.restResources()) {
            if (resourceClass.isAnnotationPresent(Provider.class)
                    || resourceClass.isAnnotationPresent(Dependent.class)) {
                // Two cases register as Class<?> instead of via
                // CDI-resolved singleton:
                //
                // - @Provider: CDI normal-scope client-proxy classes
                //   don't carry the bean class's annotations on the
                //   proxy type itself, so a CDI-resolved singleton
                //   is invisible as a JAX-RS provider. Registering
                //   the class directly lets JAX-RS see @Provider
                //   and integrate the type as ExceptionMapper /
                //   MessageBodyReader / filter / etc.
                //
                // - @Dependent: CDI.current().select(class).get()
                //   returns one fresh instance per call. Registering
                //   a singleton would fix that instance for the whole
                //   server lifetime, breaking the "new instance per
                //   request" expectation of JAX-RS's default
                //   resource-instance lifecycle (which is what users
                //   reach for when they write @Dependent on a resource).
                //
                // Trade-off in both cases: the resource/provider is
                // instantiated by the JAX-RS runtime, not by CDI, so
                // @Inject inside it is NOT satisfied. Typical
                // stateless ExceptionMappers and per-request
                // @Dependent resources don't need that.
                classes.add(resourceClass);
            } else {
                singletons.add(CDI.current().select(resourceClass).get());
            }
        }

        Application application = new TestApplication(classes, singletons);

        SeBootstrap.Configuration configuration = SeBootstrap.Configuration.builder()
                .protocol("HTTP")
                .host("localhost")
                .port(0)
                .rootPath("/")
                .build();

        SeBootstrap.Instance instance = startServer(application, configuration);

        try {
            int resolvedPort = instance.configuration().port();
            String baseUrl = "http://localhost:" + resolvedPort;
            CDI.current().select(TestUrlHolder.class).get().setBaseUrl(baseUrl);
            testContext.bindMetadata(SeBootstrap.Instance.class, instance);
        } catch (RuntimeException e) {
            stopServerQuietly(instance);
            throw e;
        }
    }

    @Override
    public void afterAll(TestContext testContext) {
        Optional<SeBootstrap.Instance> instance =
                testContext.getMetadata(SeBootstrap.Instance.class);
        if (instance.isEmpty()) {
            return;
        }
        try {
            stopServerQuietly(instance.get());
        } finally {
            try {
                CDI.current().select(TestUrlHolder.class).get().clear();
            } catch (RuntimeException cdiAlreadyClosing) {
                LOGGER.log(Logger.Level.DEBUG,
                        "TestUrlHolder.clear skipped — CDI container already closing",
                        cdiAlreadyClosing);
            }
            testContext.unbindMetadata(SeBootstrap.Instance.class);
        }
    }

    private static void probeJaxRsRuntime() {
        try {
            RuntimeDelegate.getInstance();
        } catch (RuntimeException | LinkageError absent) {
            throw new IllegalStateException("No JAX-RS SeBootstrap implementation found", absent);
        }
    }

    private static SeBootstrap.Instance startServer(
            Application application, SeBootstrap.Configuration configuration) {
        try {
            return SeBootstrap.start(application, configuration)
                    .toCompletableFuture()
                    .get(SERVER_START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting JAX-RS server", interrupted);
        } catch (ExecutionException | TimeoutException startFailure) {
            throw new IllegalStateException("Failed to start JAX-RS server", startFailure);
        }
    }

    private static void stopServerQuietly(SeBootstrap.Instance instance) {
        try {
            instance.stop().toCompletableFuture().get(SERVER_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOGGER.log(Logger.Level.WARNING, "Interrupted while stopping JAX-RS server");
        } catch (ExecutionException | TimeoutException | RuntimeException stopFailure) {
            LOGGER.log(Logger.Level.WARNING, "Failed to stop JAX-RS server", stopFailure);
        }
    }

    /**
     * Internal {@link Application} subclass that exposes the
     * jaxrs-module-managed {@code @Provider} classes (currently
     * just {@link CdiIntegrationFilter}, instantiated by the
     * JAX-RS runtime itself) and the user's resource singletons
     * (pre-resolved through CDI so {@code @Inject} dependencies
     * are satisfied on every dispatch). Splitting on
     * classes-vs-singletons sidesteps each provider's
     * vendor-specific CDI integration: jaxrs-module owns the CDI
     * lookup, the provider just sees instances. A named class
     * (rather than an anonymous one) so the JAX-RS runtime's
     * debug logging surfaces a stable diagnostic name.
     */
    static class TestApplication extends Application {

        private final Set<Class<?>> classes;
        private final Set<Object> singletons;

        TestApplication(Set<Class<?>> classes, Set<Object> singletons) {
            this.classes = Set.copyOf(classes);
            this.singletons = Set.copyOf(singletons);
        }

        @Override
        public Set<Class<?>> getClasses() {
            return classes;
        }

        @Override
        public Set<Object> getSingletons() {
            return singletons;
        }
    }
}
