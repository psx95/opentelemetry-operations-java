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
package com.google.cloud.opentelemetry.extension.auth.testapp;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class InstrumentedTestApp {
  private static final String serverUrl = "http://localhost:%d/%s";
  private static final int defaultPort = 8080;

  public static void main(String[] args) throws IOException, InterruptedException {
    int port = parsePort(args);

    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/doWork", new TestHandler());
    server.createContext(
        "/stop",
        exchange -> {
          String response = "Stopping Server";
          System.out.println(response);
          exchange.sendResponseHeaders(200, response.length());
          OutputStream os = exchange.getResponseBody();
          os.write(response.getBytes());
          os.close();
          server.stop(0);
        });
    server.setExecutor(null); // creates a default executor
    server.start();
    System.out.println("Server ready");
    System.out.println("Sending request to do work ...");
    makeCall(String.format(serverUrl, port, "doWork"));
    Thread.sleep(1000);
    System.out.println("Sending request to stop server ...");
    makeCall(String.format(serverUrl, port, "stop"));
  }

  // Make calls to the server using Apache HttpClient
  private static void makeCall(String url) {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet request = new HttpGet(url);
      try (CloseableHttpResponse response = httpClient.execute(request)) {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          String responseBody = EntityUtils.toString(entity);
          System.out.println("Response Body: " + responseBody);
          EntityUtils.consume(entity);
        }
      }
    } catch (IOException e) {
      System.err.println("Error making request: " + e.getMessage());
    }
  }

  private static int parsePort(String[] args) {
    int port;
    if (args.length > 0) {
      try {
        port = Integer.parseInt(args[0]);
        if (port < 0 || port > 65535) {
          throw new NumberFormatException("Port number must be between 0 and 65535");
        }
        return port;
      } catch (NumberFormatException e) {
        System.err.println("Invalid port number provided: " + args[0]);
        System.err.println("Using default port: " + defaultPort);
      }
    }
    return defaultPort;
  }
}
