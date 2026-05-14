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
package org.os890.jawelte.module.jaxrs.impl.adapter.filter;

import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS request/response filter pair that activates and
 * deactivates the CDI {@code @RequestScoped} context around every
 * HTTP request the embedded server dispatches.
 *
 * <p>JAX-RS does not by default activate the CDI request context on
 * the server thread that handles an incoming HTTP request, so any
 * {@code @RequestScoped} bean injected into a resource would fail
 * with {@code ContextNotActiveException} on first access. This
 * filter pair bridges that gap by acquiring a
 * {@link RequestContextController} from CDI when the request
 * arrives, activating the context, and storing the controller on
 * the {@link ContainerRequestContext} so the response filter can
 * deactivate it again after the response is written.
 *
 * <p>The controller's {@code activate()} returns {@code false} when
 * the context was already active on this thread (e.g. because an
 * outer interceptor or test-side scope manager activated it first);
 * in that case the filter records nothing and the response filter
 * is a no-op — the outer activator is responsible for deactivation.
 *
 * <p>Registered with the {@code SeBootstrap} server by
 * {@code JaxRsLifecycleAdapter}, which includes
 * {@code CdiIntegrationFilter.class} in the
 * {@code Application.getClasses()} set passed to
 * {@code SeBootstrap.start}. JAX-RS instantiates one shared filter
 * instance for the lifetime of the server.
 *
 * <p>{@code @Provider} so JAX-RS discovers the filter via standard
 * provider scanning even when consumers wire the filter through
 * non-jaxrs-module mechanisms (e.g. their own application
 * subclass).
 */
@Provider
public class CdiIntegrationFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String CONTROLLER_PROPERTY =
            CdiIntegrationFilter.class.getName() + ".controller";

    /** No-arg constructor instantiated by the JAX-RS runtime. */
    public CdiIntegrationFilter() {
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        RequestContextController controller =
                CDI.current().select(RequestContextController.class).get();
        if (controller.activate()) {
            requestContext.setProperty(CONTROLLER_PROPERTY, controller);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object stored = requestContext.getProperty(CONTROLLER_PROPERTY);
        if (stored instanceof RequestContextController controller) {
            controller.deactivate();
            requestContext.removeProperty(CONTROLLER_PROPERTY);
        }
    }
}
