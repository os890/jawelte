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
package example.stateless;

import jakarta.ejb.Stateless;

/**
 * @jakarta.ejb.Stateless is rewritten by ejb-module's CDI extension
 * to @Dependent — per-injection-point fresh instance, no state
 * carried across @Test methods (each method's @Inject site resolves
 * a fresh instance).
 */
@Stateless
public class Counter {

    private int value;

    public int increment() {
        return ++value;
    }
}
