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
package org.os890.jawelte.tests.jaxrs.scenario11;

/**
 * Custom unchecked exception thrown by
 * {@link Scenario11Resource}; mapped to HTTP 418 by
 * {@link Scenario11TeapotExceptionMapper}.
 */
public class Scenario11TeapotException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Default no-arg constructor. */
    public Scenario11TeapotException() {
        super("I'm a teapot");
    }
}
