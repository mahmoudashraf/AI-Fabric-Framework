package com.loomai.desk;

import static com.loomai.desk.ticket.TicketContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SupportSpaceConversationContractTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"conversation\":null}",
        "{\"conversation\":{}}",
        "{\"conversation\":{\"mode\":null}}"
      })
  void omittedModeIsExplicitExecutorAndPositionIsNotInvented(String input) throws Exception {
    var settings = DeskIsolationTest.JSON.readValue(input, SupportSpaceInput.class).conversation();
    assertThat(settings.mode()).isEqualTo("executor");
    assertThat(settings.position()).isNull();
    assertThat(DeskIsolationTest.JSON.writeValueAsString(settings))
        .isEqualTo("{\"mode\":\"executor\"}");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "conversational",
        "navigator",
        "navigator_deep",
        "thinker_deep",
        "cart_assistant",
        "executor",
        "thinker",
        "support_assistant",
        "future-support.v2",
        "Unknown_But_Configured"
      })
  void serverConfiguredModeCodesRoundTripWithoutCatalogueChecksOrFallback(String mode)
      throws Exception {
    var input =
        DeskIsolationTest.JSON.createObjectNode().put("mode", mode).put("position", "search");
    var settings = DeskIsolationTest.JSON.treeToValue(input, ConversationSettings.class);
    com.fasterxml.jackson.databind.JsonNode output = DeskIsolationTest.JSON.valueToTree(settings);
    assertThat(output).isEqualTo(input);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "landing",
        "support",
        "troubleshooting",
        "future_help-v2",
        "Unknown_But_Configured"
      })
  void serverConfiguredPositionCodesArePreservedWithoutCatalogueChecks(String position)
      throws Exception {
    var input =
        DeskIsolationTest.JSON
            .createObjectNode()
            .put("mode", "support_assistant")
            .put("position", position);
    var settings = DeskIsolationTest.JSON.treeToValue(input, ConversationSettings.class);
    com.fasterxml.jackson.databind.JsonNode output = DeskIsolationTest.JSON.valueToTree(settings);
    assertThat(output).isEqualTo(input);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "bad code", "bad/code", "bad\ncode", "bad:code"})
  void onlyMalformedCodeSyntaxIsRejected(String code) {
    for (String field : new String[] {"mode", "position"}) {
      var input =
          DeskIsolationTest.JSON.createObjectNode().put("mode", "executor").put(field, code);
      assertThatThrownBy(
              () -> DeskIsolationTest.JSON.treeToValue(input, ConversationSettings.class))
          .isInstanceOf(JsonMappingException.class);
    }
  }

  @Test
  void codeSizeIsBoundedWithoutAnActiveCatalogueAssumption() {
    String limit = "x".repeat(64);
    var settings = new ConversationSettings(limit, limit);
    assertThat(settings.mode()).isEqualTo(limit);
    assertThat(settings.position()).isEqualTo(limit);
    assertThatThrownBy(() -> new ConversationSettings(limit + "x", null))
        .isInstanceOf(com.loomai.desk.ticket.TicketException.class);
    assertThatThrownBy(() -> new ConversationSettings("executor", limit + "x"))
        .isInstanceOf(com.loomai.desk.ticket.TicketException.class);
  }

  @Test
  void unknownCodesAreForwardedAsConfigurationRatherThanDefaulted() throws Exception {
    var settings =
        DeskIsolationTest.JSON.readValue(
            "{\"mode\":\"unknown\",\"position\":\"unknown\"}", ConversationSettings.class);
    assertThat(settings.mode()).isEqualTo("unknown");
    assertThat(settings.position()).isEqualTo("unknown");
  }
}
