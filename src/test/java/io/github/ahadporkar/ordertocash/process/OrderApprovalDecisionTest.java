package io.github.ahadporkar.ordertocash.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.process.test.api.CamundaProcessTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The {@code order-approval} decision table, evaluated by the engine, at its boundaries. */
@CamundaProcessTest
class OrderApprovalDecisionTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private CamundaClient client;

  @BeforeEach
  void deployDecision() {
    client.newDeployResourceCommand().addResourceFromClasspath("dmn/order-approval.dmn").send().join();
  }

  @ParameterizedTest(name = "{0} customer, {1} EUR -> approval {2}, pay within {3}")
  @CsvSource({
      // segment, total,   approval, term,  reminder, rule
      "GOLD,      60000,    true,     P30D,  P21D,     1",
      "GOLD,      49999.99, false,    P30D,  P21D,     2",
      "NEW,       1000.01,  true,     P7D,   P5D,      3",
      "NEW,       1000,     false,    P7D,   P5D,      4",
      "STANDARD,  10000.01, true,     P14D,  P10D,     5",
      "STANDARD,  10000,    false,    P14D,  P10D,     6",
      "STANDARD,  5,        false,    P14D,  P10D,     6"
  })
  void decidesApprovalAndPaymentTerms(String segment, double total, boolean approvalRequired,
      String paymentTerm, String reminderAfter, int expectedRule) throws Exception {

    EvaluateDecisionResponse response = client.newEvaluateDecisionCommand()
        .decisionId(ProcessConstants.DECISION_ID)
        .variables(Map.of(ProcessConstants.CUSTOMER_SEGMENT, segment, ProcessConstants.TOTAL_AMOUNT, total))
        .send()
        .join();

    JsonNode output = JSON.readTree(response.getDecisionOutput());
    assertThat(output.get("approvalRequired").asBoolean()).isEqualTo(approvalRequired);
    assertThat(output.get("paymentTerm").asText()).isEqualTo(paymentTerm);
    assertThat(output.get("reminderAfter").asText()).isEqualTo(reminderAfter);
    assertThat(response.getEvaluatedDecisions().get(0).getMatchedRules())
        .extracting(MatchedDecisionRule::getRuleIndex)
        .containsExactly(expectedRule);
  }
}
