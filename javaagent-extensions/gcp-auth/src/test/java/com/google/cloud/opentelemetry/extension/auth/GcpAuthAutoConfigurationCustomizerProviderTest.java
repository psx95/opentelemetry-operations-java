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

import static com.google.cloud.opentelemetry.extension.auth.GcpAuthAutoConfigurationCustomizerProvider.GCP_USER_PROJECT_ID_KEY;
import static com.google.cloud.opentelemetry.extension.auth.GcpAuthAutoConfigurationCustomizerProvider.QUOTA_USER_PROJECT_HEADER;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.internal.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GcpAuthAutoConfigurationCustomizerProviderTest {

  @Mock private GoogleCredentials mockedGoogleCredentials;

  @Captor private ArgumentCaptor<Collection<SpanData>> spanDataCollectionCaptor;
  @Captor private ArgumentCaptor<Supplier<Map<String, String>>> headerSupplierCaptor;

  private final Map<String, String> otelProperties =
      ImmutableMap.of(
          "otel.bsp.schedule.delay", // span exporter
          "10",
          "otel.traces.exporter",
          "otlp",
          "otel.metrics.exporter",
          "none",
          "otel.logs.exporter",
          "none");

  @BeforeEach
  public void setup() {
    MockitoAnnotations.openMocks(this);
    Mockito.when(mockedGoogleCredentials.getQuotaProjectId()).thenReturn("test-project");
    Mockito.when(mockedGoogleCredentials.getAccessToken())
        .thenReturn(new AccessToken("fake", new Date()));
  }

  @Test
  public void testCustomizerOtlpHttp() {
    OtlpHttpSpanExporter mockOtlpHttpSpanExporter = Mockito.mock(OtlpHttpSpanExporter.class);
    OtlpHttpSpanExporterBuilder otlpSpanExporterBuilder = OtlpHttpSpanExporter.builder();
    OtlpHttpSpanExporterBuilder spyOtlpHttpSpanExporterBuilder =
        Mockito.spy(otlpSpanExporterBuilder);
    Mockito.when(spyOtlpHttpSpanExporterBuilder.build()).thenReturn(mockOtlpHttpSpanExporter);

    Mockito.when(mockOtlpHttpSpanExporter.export(Mockito.anyCollection()))
        .thenReturn(CompletableResultCode.ofSuccess());
    Mockito.when(mockOtlpHttpSpanExporter.toBuilder()).thenReturn(spyOtlpHttpSpanExporterBuilder);

    try (MockedStatic<GoogleCredentials> googleCredentialsMockedStatic =
        Mockito.mockStatic(GoogleCredentials.class)) {
      googleCredentialsMockedStatic
          .when(GoogleCredentials::getApplicationDefault)
          .thenReturn(mockedGoogleCredentials);

      OpenTelemetrySdk sdk = buildOpenTelemetrySdkWithExporter(mockOtlpHttpSpanExporter);
      generateTestSpan(sdk);

      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                Mockito.verify(mockOtlpHttpSpanExporter, Mockito.times(1)).toBuilder();
                Mockito.verify(spyOtlpHttpSpanExporterBuilder, Mockito.times(1))
                    .setHeaders(headerSupplierCaptor.capture());
                assertEquals(2, headerSupplierCaptor.getValue().get().size());
                verifyAuthHeaders(headerSupplierCaptor.getValue().get());

                Mockito.verify(mockOtlpHttpSpanExporter, Mockito.atLeast(1))
                    .export(spanDataCollectionCaptor.capture());
                spanDataCollectionCaptor
                    .getValue()
                    .forEach(
                        spanData -> {
                          assertEquals(
                              "test-project",
                              spanData
                                  .getAttributes()
                                  .get(AttributeKey.stringKey(GCP_USER_PROJECT_ID_KEY)));
                          assertTrue(
                              spanData
                                  .getAttributes()
                                  .asMap()
                                  .containsKey(AttributeKey.stringKey("work_loop")));
                        });
              });
    }
  }

  @Test
  public void testCustomizerOtlpGrpc() {
    OtlpGrpcSpanExporter mockOtlpGrpcSpanExporter = Mockito.mock(OtlpGrpcSpanExporter.class);
    OtlpGrpcSpanExporterBuilder otlpSpanExporterBuilder = OtlpGrpcSpanExporter.builder();
    OtlpGrpcSpanExporterBuilder spyOtlpGrpcSpanExporterBuilder =
        Mockito.spy(otlpSpanExporterBuilder);
    Mockito.when(spyOtlpGrpcSpanExporterBuilder.build()).thenReturn(mockOtlpGrpcSpanExporter);

    Mockito.when(mockOtlpGrpcSpanExporter.export(Mockito.anyCollection()))
        .thenReturn(CompletableResultCode.ofSuccess());
    Mockito.when(mockOtlpGrpcSpanExporter.toBuilder()).thenReturn(spyOtlpGrpcSpanExporterBuilder);

    try (MockedStatic<GoogleCredentials> googleCredentialsMockedStatic =
        Mockito.mockStatic(GoogleCredentials.class)) {
      googleCredentialsMockedStatic
          .when(GoogleCredentials::getApplicationDefault)
          .thenReturn(mockedGoogleCredentials);

      OpenTelemetrySdk sdk = buildOpenTelemetrySdkWithExporter(mockOtlpGrpcSpanExporter);
      generateTestSpan(sdk);

      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                Mockito.verify(mockOtlpGrpcSpanExporter, Mockito.times(1)).toBuilder();
                Mockito.verify(spyOtlpGrpcSpanExporterBuilder, Mockito.times(1))
                    .setHeaders(headerSupplierCaptor.capture());
                assertEquals(2, headerSupplierCaptor.getValue().get().size());
                verifyAuthHeaders(headerSupplierCaptor.getValue().get());

                Mockito.verify(mockOtlpGrpcSpanExporter, Mockito.atLeast(1))
                    .export(spanDataCollectionCaptor.capture());
                spanDataCollectionCaptor
                    .getValue()
                    .forEach(
                        spanData -> {
                          assertEquals(
                              "test-project",
                              spanData
                                  .getAttributes()
                                  .get(AttributeKey.stringKey(GCP_USER_PROJECT_ID_KEY)));
                          assertTrue(
                              spanData
                                  .getAttributes()
                                  .asMap()
                                  .containsKey(AttributeKey.stringKey("work_loop")));
                        });
              });
    }
  }

  private OpenTelemetrySdk buildOpenTelemetrySdkWithExporter(SpanExporter spanExporter) {
    SpiHelper spiHelper =
        SpiHelper.create(GcpAuthAutoConfigurationCustomizerProviderTest.class.getClassLoader());
    AutoConfiguredOpenTelemetrySdkBuilder builder =
        AutoConfiguredOpenTelemetrySdk.builder().addPropertiesSupplier(() -> otelProperties);
    AutoConfigureUtil.setComponentLoader(
        builder,
        new ComponentLoader() {
          @SuppressWarnings("unchecked")
          @Override
          public <T> Iterable<T> load(Class<T> spiClass) {
            if (spiClass == ConfigurableSpanExporterProvider.class) {
              return Collections.singletonList(
                  (T)
                      new ConfigurableSpanExporterProvider() {
                        @Override
                        public SpanExporter createExporter(ConfigProperties configProperties) {
                          return spanExporter;
                        }

                        @Override
                        public String getName() {
                          return "otlp";
                        }
                      });
            }
            return spiHelper.load(spiClass);
          }
        });
    return builder.build().getOpenTelemetrySdk();
  }

  private void verifyAuthHeaders(Map<String, String> headers) {
    Set<Entry<String, String>> headerEntrySet = headers.entrySet();
    assertTrue(
        headerEntrySet.contains(new SimpleEntry<>(QUOTA_USER_PROJECT_HEADER, "test-project")));
    assertTrue(headerEntrySet.contains(new SimpleEntry<>("Authorization", "Bearer fake")));
  }

  private void generateTestSpan(OpenTelemetrySdk openTelemetrySdk) {
    Span span = openTelemetrySdk.getTracer("test").spanBuilder("sample").startSpan();
    try (Scope ignored = span.makeCurrent()) {
      long workOutput = busyloop();
      span.setAttribute("work_loop", workOutput);
    } finally {
      span.end();
    }
  }

  // loop to simulate work done
  private long busyloop() {
    Instant start = Instant.now();
    Instant end;
    long counter = 0;
    do {
      counter++;
      end = Instant.now();
    } while (Duration.between(start, end).toMillis() < 1000);
    System.out.println("Busy work done, counted " + counter + " times in one second.");
    return counter;
  }
}
