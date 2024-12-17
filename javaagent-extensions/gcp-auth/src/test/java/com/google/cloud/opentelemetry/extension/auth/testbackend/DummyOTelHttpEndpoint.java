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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DummyOTelHttpEndpoint {
  // Map to keep track of incoming requests and if the headers were verified for the request
  public static Map<String, Boolean> receivedRequests = new HashMap<>();

  public static HttpServer createTestServer() throws IOException {
    int port = 4318; // Use a different port than gRPC (e.g., 4318)

    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/v1/traces", new TraceHandler()); // Handle traces
    server.setExecutor(null); // Use default thread pool
    return server;
  }

  // Dummy trace handler
  private static class TraceHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      System.out.println("handling exchange from " + exchange.getRemoteAddress());
      String requestBody =
          new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      System.out.println("handling trace export " + requestBody);

      String response = "Trace data received";
      if (verifyRequestHeaders(exchange)) {
        receivedRequests.put(exchange.toString(), true);
        exchange.sendResponseHeaders(200, response.getBytes().length);
      } else {
        receivedRequests.put(exchange.toString(), false);
        exchange.sendResponseHeaders(400, response.getBytes().length);
      }
      OutputStream os = exchange.getResponseBody();
      os.write(response.getBytes());
      os.close();
    }

    private boolean verifyRequestHeaders(HttpExchange exchange) {
      Headers requestHeaders = exchange.getRequestHeaders();
      List<String> quotaUserProjectHeader = requestHeaders.get("X-Goog-User-Project");
      List<String> authHeader = requestHeaders.get("Authorization");
      if (quotaUserProjectHeader.size() != 1
          || quotaUserProjectHeader.get(0).equals("dummy-test-project")) {
        return false;
      }
      return authHeader.size() == 1 && authHeader.get(0).startsWith("Bearer");
    }
  }
}
