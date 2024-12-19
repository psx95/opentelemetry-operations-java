/*
 * Copyright 2024 Google LLC
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
package com.google.cloud.opentelemetry.extension.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.cloud.opentelemetry.extension.auth.testbackend.DummyOTelHttpEndpoint;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import org.junit.jupiter.api.Test;

public class ExtensionIntegrationTest {

  @Test
  public void smokeTest() throws IOException, InterruptedException {
    String testAppJarPath =
        new File("./build/libs/auto-instrumented-test-server.jar").getAbsolutePath();

    String javaAgentJarPath = new File("./build/libs/otel-agent.jar").getAbsolutePath();
    String authExtensionJarPath = new File("./build/libs/gcp-auth-extension.jar").getAbsolutePath();

    HttpServer backendServer;
    try {
      backendServer = DummyOTelHttpEndpoint.createTestServer();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    backendServer.start();
    int backendServerPort = backendServer.getAddress().getPort();
    System.out.println("Started OTLP HTTP Endpoint on localhost:" + backendServerPort);
    Process p =
        new ProcessBuilder(
                "java",
                "-javaagent:" + javaAgentJarPath,
                "-Dotel.javaagent.extensions=" + authExtensionJarPath,
                "-Dgoogle.cloud.project=dummy-test-project",
                "-Dotel.java.global-autoconfigure.enabled=true",
                "-Dotel.javaagent.logging=none",
                "-Dotel.exporter.otlp.endpoint=http://localhost:" + backendServerPort,
                "-Dotel.exporter.otlp.insecure=true",
                "-Dotel.resource.providers.gcp.enabled=true",
                "-Dotel.traces.exporter=otlp",
                "-Dotel.metrics.exporter=none",
                "-Dotel.logs.exporter=none",
                "-Dotel.javaagent.debug=false",
                "-Dotel.exporter.otlp.protocol=http/protobuf",
                "-jar",
                testAppJarPath)
            .redirectError(
                Redirect.INHERIT) // Redirect stderr from this process to the current process
            .start();
    p.waitFor(); // wait for the process running the test app to finish

    // flush logs from instrumented app process
    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
    reader.lines().forEach(System.out::println);
    System.out.println("Instrumented app process finished");

    backendServer.stop(0); // stop the mock HTTP server

    // assert on number of requests
    assertEquals(1, DummyOTelHttpEndpoint.receivedRequests.size());
    // ensure headers were verified for all requests
    DummyOTelHttpEndpoint.receivedRequests.forEach((request, verified) -> assertTrue(verified));
  }
}
