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
package org.os890.jawelte.tests.lnp.scenario07;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * Gatling simulation that drives the same 5-step CRUD roundtrip as
 * scenario-06 against a {@code CustomerResource} hosted by
 * jaxrs-module's embedded SeBootstrap server. 10 virtual users hit
 * the endpoint at once; each user walks
 * list&nbsp;→&nbsp;create&nbsp;→&nbsp;read&nbsp;→&nbsp;delete&nbsp;→&nbsp;list,
 * producing ~50 HTTP calls per test class. Gatling's global
 * assertions check that no request failed and that the slowest
 * response stayed under one second — that's what fails the JUnit
 * test method when something regresses.
 *
 * <p>The base URL is supplied via the system property
 * {@code gatling.baseUrl}, populated by the abstract test base from
 * the {@code TestUrl} CDI bean before
 * {@code io.gatling.app.Gatling.fromMap(...)} is invoked.
 *
 * <p>This class is public on purpose — Gatling reflectively
 * instantiates it via its no-arg constructor when the simulation
 * runner picks it up by FQCN.
 */
public class CustomerCrudSimulation extends Simulation {

    private static final int VIRTUAL_USERS = 10;
    private static final int MAX_RESPONSE_TIME_MILLIS = 1_000;

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(System.getProperty("gatling.baseUrl"))
            .acceptHeader("application/json");

    private final ScenarioBuilder roundtrip = scenario("customer-crud-roundtrip")
            .exec(http("list-initial").get("/customers")
                    .check(status().is(200)))
            .exec(http("create").post("/customers?name=Gat-#{userId}&email=gat-#{userId}@test.com")
                    .check(status().is(200))
                    .check(jsonPath("$.id").saveAs("newId")))
            .exec(http("read").get("/customers/#{newId}")
                    .check(status().is(200)))
            .exec(http("delete").delete("/customers/#{newId}")
                    .check(status().is(200)))
            .exec(http("list-final").get("/customers")
                    .check(status().is(200)));

    /** Default constructor required by Gatling's reflective bootstrap. */
    public CustomerCrudSimulation() {
        setUp(
                roundtrip.injectOpen(atOnceUsers(VIRTUAL_USERS))
        ).protocols(httpProtocol).assertions(
                global().failedRequests().count().is(0L),
                global().responseTime().max().lt(MAX_RESPONSE_TIME_MILLIS));
    }
}
