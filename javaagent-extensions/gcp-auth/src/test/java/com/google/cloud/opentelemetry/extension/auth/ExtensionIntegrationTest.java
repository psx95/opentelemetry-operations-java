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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class ExtensionIntegrationTest {

  private static final String TEST_SERVER_PATH = "/doWork";
  private static GenericContainer<?> applicationContainer;
  private static Integer applicationPort;

  @BeforeAll
  static void setup() {
    String testAppJarPath =
        new File("./build/libs/auto-instrumented-test-server.jar").getAbsolutePath();
    String javaAgentJarPath = new File("./build/libs/otel-agent.jar").getAbsolutePath();
    String authExtensionJarPath = new File("./build/libs/gcp-auth-extension.jar").getAbsolutePath();
    DockerImageName dockerBaseImage = DockerImageName.parse("openjdk:17-jdk-slim");
    int preferredApplicationPort = 8000;

    applicationContainer =
        new GenericContainer<>(dockerBaseImage)
            .withExposedPorts(preferredApplicationPort)
            .withCopyFileToContainer(MountableFile.forHostPath(testAppJarPath), "/test-app.jar")
            .withCopyFileToContainer(MountableFile.forHostPath(javaAgentJarPath), "/agent.jar")
            .withCopyFileToContainer(
                MountableFile.forHostPath(authExtensionJarPath), "/auth-ext.jar")
            .withCommand(
                "java",
                "-javaagent:/agent.jar",
                "-Dotel.javaagent.extensions=/auth-ext.jar",
                "-Dotel.java.global-autoconfigure.enabled=true",
                "-Dgoogle.cloud.project=dummy-test-project",
                "-Dgoogle.cloud.region=us-central1",
                "-Dotel.traces.exporter=otlp,logging",
                "-Dotel.metrics.exporter=none",
                "-jar",
                "/test-app.jar",
                Integer.toString(preferredApplicationPort))
            .waitingFor(Wait.forLogMessage(".*Waiting for requests.*", 1));
    applicationContainer.start();
    applicationPort = applicationContainer.getMappedPort(preferredApplicationPort);
  }

  @AfterAll
  static void tearDown() {
    if (applicationContainer != null) {
      applicationContainer.stop();
    }
  }

  @Test
  public void testServerResponding() throws IOException, InterruptedException {
    String url =
        "http://" + applicationContainer.getHost() + ":" + applicationPort + TEST_SERVER_PATH;
    System.out.println("URL: " + url);
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
  }
}
