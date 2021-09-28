package org.xrpl.xrpl4j.samples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import okhttp3.HttpUrl;
import org.immutables.value.Value.Immutable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.model.client.server.ServerInfo;
import org.xrpl.xrpl4j.model.jackson.ObjectMapperFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * This test attempts to reproduce the error reported in issue 156 where {@link ZonedDateTime} is not able to be
 * deserialized on certain JVM platforms.
 *
 * @see "https://github.com/XRPLF/xrpl4j/issues/156"
 */
public class ZonedDateTimeTests {

  private static final String URL_ALTNET_TESTNET = "https://s.altnet.rippletest.net:51234/";
  private static final Logger LOGGER = LoggerFactory.getLogger(ZonedDateTimeTests.class);

  private XrplClient xrplClient;

  @BeforeEach
  void setUp() {
    HttpUrl rippledUrl = HttpUrl.get(URL_ALTNET_TESTNET);
    xrplClient = new XrplClient(rippledUrl);
  }

  @Test
  void testDeserializeServerInfo() throws JsonRpcClientErrorException {
    ServerInfo info = xrplClient.serverInfo();
    assertNotNull(info);

    LOGGER.info("ServerInfo: {}", info);
  }

  @Test
  void testDeserializeZonedDateTime() throws JsonProcessingException {
    String expectedTimeAsString = "2021-Sep-27 11:43:47.464662 UTC";
    String expectedJson = "{" +
      "\"time\":\"" + expectedTimeAsString + "\"" +
      "}";

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss.SSSSSS z");
    ZoneDateTimeHolder zoneDateTimeHolder = ImmutableZoneDateTimeHolder.builder()
      .time(ZonedDateTime.parse(expectedTimeAsString, formatter))
      .build();

    final ObjectMapper objectMapper = ObjectMapperFactory.create();
    String actualJson = objectMapper.writeValueAsString(zoneDateTimeHolder);
    assertThat(actualJson).isEqualTo(expectedJson);

    ZoneDateTimeHolder holder = ObjectMapperFactory.create().readValue(expectedJson, ZoneDateTimeHolder.class);
    assertThat(holder).isNotNull();
  }

  @Immutable
  @JsonSerialize(as = ImmutableZoneDateTimeHolder.class)
  @JsonDeserialize(as = ImmutableZoneDateTimeHolder.class)
  interface ZoneDateTimeHolder {

    /**
     * The current time in UTC, according to the server's clock.
     *
     * @return A {@link ZonedDateTime} denoting the server clock time.
     */
    @JsonFormat(pattern = "yyyy-MMM-dd HH:mm:ss.SSSSSS z")
    ZonedDateTime time();
  }
}
