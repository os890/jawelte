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
package org.os890.jawelte.tests.resource.scenario03;

import javax.sql.DataSource;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Asks for a name nothing is bound under - one character away from the
 * one the declaration actually uses, which is how this happens in
 * practice.
 */
@ApplicationScoped
public class TypoRepository {

    @Resource(lookup = "java:app/jdbc/AppDSS")
    private DataSource declared;

    /** No-arg constructor required by CDI. */
    public TypoRepository() {
    }

    /**
     * @return the field, never reached in this scenario
     */
    public DataSource declared() {
        return declared;
    }
}
