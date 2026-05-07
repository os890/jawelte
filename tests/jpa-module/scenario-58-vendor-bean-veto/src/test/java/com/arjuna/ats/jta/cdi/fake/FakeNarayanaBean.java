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
package com.arjuna.ats.jta.cdi.fake;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Stand-in for a Narayana CDI bean that lands on the test
 * classpath. Lives in {@code com.arjuna.ats.jta.cdi.*} so
 * {@code JpaCdiExtension}'s {@code @Observes ProcessAnnotatedType}
 * vetoes it. The CDI container must therefore resolve it as
 * {@code Unsatisfied}.
 */
@ApplicationScoped
public class FakeNarayanaBean {

    /** Default constructor required by CDI. */
    public FakeNarayanaBean() {
    }
}
