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
package example.mapper;

import jakarta.ejb.Stateful;

/**
 * @jakarta.ejb.Stateful is not handled by ejb-module's default
 * mapper. Without a custom mapper this class would be discovered
 * but left without a CDI scope, and @Inject would fail at deployment.
 * The custom StatefulAsRequestScopedMapper claims it and maps it to
 * @RequestScoped.
 */
@Stateful
public class ShoppingCart {

    private int itemCount;

    public int addItem() {
        return ++itemCount;
    }
}
