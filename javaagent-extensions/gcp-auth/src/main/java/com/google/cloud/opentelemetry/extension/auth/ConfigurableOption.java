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

import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.util.Locale;

/**
 * An enum representing configurable options for a GCP Authentication Extension. Each option has a
 * user-readable name and can be configured using environment variables or system properties.
 */
public enum ConfigurableOption {
  /**
   * Represents the Google Cloud Project ID option. Can be configured using the environment variable
   * `GOOGLE_CLOUD_PROJECT` or the system property `google.cloud.project`.
   */
  GOOGLE_CLOUD_PROJECT("Google Cloud Project ID");

  private static final String OPTION_NOT_CONFIGURED_MSG =
      "GCP Authentication Extension not configured properly: %s not configured. Configure it by exporting environment variable %s or system property %s";

  private final String userReadableName;
  private final String environmentVariableName;
  private final String systemPropertyName;

  ConfigurableOption(String userReadableName) {
    this.userReadableName = userReadableName;
    this.environmentVariableName = this.name();
    this.systemPropertyName =
        this.environmentVariableName.toLowerCase(Locale.ENGLISH).replace('_', '.');
  }

  /**
   * Returns the environment variable name associated with this option.
   *
   * @return the environment variable name (e.g., GOOGLE_CLOUD_PROJECT)
   */
  String getEnvironmentVariable() {
    return this.environmentVariableName;
  }

  /**
   * Returns the system property name associated with this option.
   *
   * @return the system property name (e.g., google.cloud.project)
   */
  String getSystemProperty() {
    return this.systemPropertyName;
  }

  /**
   * Retrieves the configured value for this option. This method checks the environment variable
   * first and then the system property.
   *
   * @return the configured value as a string, or throws an exception if not configured.
   * @throws ConfigurationException if neither the environment variable nor the system property is
   *     set.
   */
  String getConfiguredValue() throws ConfigurationException {
    String envVar = System.getenv(this.getEnvironmentVariable());
    String sysProp = System.getProperty(this.getSystemProperty());

    if (envVar != null && !envVar.isEmpty()) {
      return envVar;
    } else if (sysProp != null && !sysProp.isEmpty()) {
      return sysProp;
    } else {
      throw new ConfigurationException(
          String.format(
              OPTION_NOT_CONFIGURED_MSG,
              this.userReadableName,
              this.getEnvironmentVariable(),
              this.getSystemProperty()));
    }
  }
}
