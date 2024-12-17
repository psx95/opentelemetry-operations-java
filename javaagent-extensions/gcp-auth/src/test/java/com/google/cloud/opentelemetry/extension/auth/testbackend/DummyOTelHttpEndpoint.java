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
package com.google.cloud.opentelemetry.extension.auth.testbackend;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyOTelHttpEndpoint {
  public static final String HEADER_VERIFIED = "OTLP Export Headers verified";
  public static final String HEADER_NOT_VERIFIED = "OTLP Export Headers not verified";
  private static final Logger logger = LoggerFactory.getLogger(DummyOTelHttpEndpoint.class);

  public static void main(String[] args) throws IOException {
    int port = 4318; // Use a different port than gRPC (e.g., 4318)

    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/v1/traces", new TraceHandler()); // Handle traces
    server.setExecutor(Executors.newFixedThreadPool(4)); // Thread pool for handling requests
    server.start();

    logger.info("Dummy OpenTelemetry HTTP endpoint started on port {}", port);
    logger.info("Send POST requests to /v1/traces");
  }

  static class TraceHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if ("POST".equals(exchange.getRequestMethod())) {
        String requestBody =
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        logger.info("Received trace data:\n" + requestBody);
        String response = "Trace data received";
        if (verifyRequestHeaders(exchange)) {
          exchange.sendResponseHeaders(200, response.getBytes().length);
          logger.info(HEADER_VERIFIED);
        } else {
          exchange.sendResponseHeaders(400, response.getBytes().length);
          logger.info(HEADER_NOT_VERIFIED);
        }
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
      } else {
        sendMethodNotAllowed(exchange);
      }
    }

    private boolean verifyRequestHeaders(HttpExchange exchange) {
      Headers requestHeaders = exchange.getRequestHeaders();
      List<String> quotaUserProjectHeader = requestHeaders.get("X-Goog-User-Project");
      List<String> authHeader = requestHeaders.get("Authorization");
      if (quotaUserProjectHeader.size() != 1
          || quotaUserProjectHeader.get(0).equals("dummy-test-project")) {
        return false;
      }
      if (authHeader.size() != 1 || !authHeader.get(0).startsWith("Bearer")) {
        return false;
      }
      return true;
    }
  }

  private static void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
    String response = "Method Not Allowed";
    exchange.sendResponseHeaders(405, response.getBytes().length);
    OutputStream os = exchange.getResponseBody();
    os.write(response.getBytes());
    os.close();
  }
}
