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
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstrumentedServer {
  private static final Logger logger = LoggerFactory.getLogger(InstrumentedServer.class);
  private static final int defaultPort = 8080;
  // to run this from command line, execute `gradle run`
  public static void main(String[] args) throws InterruptedException, IOException {
    int port = parsePort(args);
    logger.info("Starting the test server on {}", port);

    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/doWork", new TestHandler());
    server.setExecutor(null); // creates a default executor
    server.start();
    logger.info("Waiting for requests");
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
        logger.warn("Invalid port number provided: {}", args[0]);
        logger.warn("Using default port: {}", defaultPort);
      }
    }
    return defaultPort;
  }
}
