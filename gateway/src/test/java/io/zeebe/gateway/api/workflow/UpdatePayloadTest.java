/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.gateway.api.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerUpdateWorkflowInstancePayloadRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateWorkflowInstancePayloadRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateWorkflowInstancePayloadResponse;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import org.junit.Test;

public class UpdatePayloadTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final UpdatePayloadStub stub = new UpdatePayloadStub();
    stub.registerWith(gateway);

    final String payload = JsonUtil.toJson(Collections.singletonMap("key", "value"));

    final UpdateWorkflowInstancePayloadRequest request =
        UpdateWorkflowInstancePayloadRequest.newBuilder()
            .setElementInstanceKey(123)
            .setPayload(payload)
            .build();

    // when
    final UpdateWorkflowInstancePayloadResponse response =
        client.updateWorkflowInstancePayload(request);

    // then
    assertThat(response).isNotNull();

    final BrokerUpdateWorkflowInstancePayloadRequest brokerRequest =
        gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(-1);
    assertThat(brokerRequest.getIntent()).isEqualTo(VariableDocumentIntent.UPDATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.VARIABLE_DOCUMENT);

    final VariableDocumentRecord brokerRequestValue = brokerRequest.getRequestWriter();
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getDocument(), payload);
    assertThat(brokerRequestValue.getScopeKey()).isEqualTo(123);
  }
}
