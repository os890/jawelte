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
package org.os890.jawelte.module.jaxrs.api;

import java.util.Objects;

import jakarta.ws.rs.core.Response;

import org.os890.jawelte.module.contentdiff.api.ContentDiff;
import org.os890.jawelte.module.contentdiff.api.JsonBuilder;
import org.os890.jawelte.module.contentdiff.api.XmlBuilder;

/**
 * Thin adapter bridging a JAX-RS {@link Response} to
 * content-diff-module's {@link ContentDiff} builders. Reads the
 * response entity as a {@code String} and forwards it to
 * {@link ContentDiff#forJson(String)} or
 * {@link ContentDiff#forXml(String)}; everything else (engine
 * selection, default-ignore-pattern resolution via MicroProfile
 * Config, ordered-vs-unordered array semantics) is content-diff's
 * concern.
 *
 * <p>jaxrs-module contributes no diff logic of its own. The two
 * MicroProfile Config default-ignore-pattern keys
 * ({@code org.os890.jawelte.module.contentdiff.api.ContentDiff.json.ignore}
 * and {@code org.os890.jawelte.module.contentdiff.api.ContentDiff.xml.ignore})
 * declared by content-diff-module apply transparently to
 * {@code ResponseDiff} callers — boilerplate patterns like
 * {@code $.timestamp} or {@code $.requestId} can be set once for
 * the JVM and inherited by every assertion that flows through
 * {@code ResponseDiff}.
 *
 * <p>Typical use:
 * <pre>{@code
 * try (Response response = client.target(testUrl.get() + "/orders/42")
 *         .request()
 *         .get()) {
 *     ResponseDiff.forJson(response)
 *             .expected("orders/order-42.json")
 *             .assertEquals();
 * }
 * }</pre>
 *
 * <p>{@code abstract} + private constructor per the project's
 * static-utility class convention (avoids accidental instantiation
 * and accidental subclassing while keeping the "no
 * {@code final} classes" project rule satisfied).
 */
public abstract class ResponseDiff {

    /**
     * Hidden constructor — {@code ResponseDiff} is a static-utility
     * class and is never instantiated.
     */
    private ResponseDiff() {
    }

    /**
     * Read the JSON entity from {@code response} and return a
     * {@link JsonBuilder} bound to it. Caller-supplied ignore
     * patterns chain on top of the
     * {@code ContentDiff.json.ignore} defaults; the builder
     * itself is single-use.
     *
     * <p>The response is expected to carry a JSON content type;
     * the check is the caller's responsibility — passing an XML
     * payload to this method will not fail here, but the JSON
     * engine will reject the malformed input when the assertion
     * runs.
     *
     * @param response the JAX-RS response; must not be
     *                 {@code null} and must carry an entity
     * @return a fresh {@link JsonBuilder} bound to the response
     *         entity content
     * @throws NullPointerException  if {@code response} is
     *                               {@code null}
     * @throws IllegalStateException with message
     *                               {@code "Response has no entity"}
     *                               if the response carries no
     *                               entity
     */
    public static JsonBuilder forJson(Response response) {
        return ContentDiff.forJson(readEntity(response));
    }

    /**
     * Read the XML entity from {@code response} and return an
     * {@link XmlBuilder} bound to it. Caller-supplied ignore
     * patterns chain on top of the
     * {@code ContentDiff.xml.ignore} defaults; the builder
     * itself is single-use.
     *
     * <p>The response is expected to carry an XML content type;
     * the check is the caller's responsibility — passing a JSON
     * payload to this method will not fail here, but the XML
     * engine will reject the malformed input when the assertion
     * runs.
     *
     * @param response the JAX-RS response; must not be
     *                 {@code null} and must carry an entity
     * @return a fresh {@link XmlBuilder} bound to the response
     *         entity content
     * @throws NullPointerException  if {@code response} is
     *                               {@code null}
     * @throws IllegalStateException with message
     *                               {@code "Response has no entity"}
     *                               if the response carries no
     *                               entity
     */
    public static XmlBuilder forXml(Response response) {
        return ContentDiff.forXml(readEntity(response));
    }

    private static String readEntity(Response response) {
        Objects.requireNonNull(response, "response");
        if (!response.hasEntity()) {
            throw new IllegalStateException("Response has no entity");
        }
        return response.readEntity(String.class);
    }
}
