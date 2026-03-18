package com.ecommerce.orderpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecommerce.orderpay.tcc.TccBranch;
import com.ecommerce.orderpay.tcc.TccPhase;
import com.ecommerce.orderpay.tcc.TccTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderPaymentFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TccTransactionRepository tccTransactionRepository;

    @Test
    void should_support_idempotent_order_and_payment_flow() throws Exception {
        String orderReq = """
            {
              "userId":"U1001",
              "checkoutToken":"CHK-001",
              "items":[{"skuId":"SKU-1","quantity":2}],
              "couponId":"COUPON-1",
              "amountCents":1200
            }
            """;

        MvcResult placeResult1 = mockMvc.perform(post("/api/v1/orders")
                .header("Idempotency-Key", "order-idem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderReq))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult placeResult2 = mockMvc.perform(post("/api/v1/orders")
                .header("Idempotency-Key", "order-idem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderReq))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode placeBody1 = objectMapper.readTree(placeResult1.getResponse().getContentAsString());
        JsonNode placeBody2 = objectMapper.readTree(placeResult2.getResponse().getContentAsString());
        long orderId1 = placeBody1.path("data").path("orderId").asLong();
        long orderId2 = placeBody2.path("data").path("orderId").asLong();
        assertThat(orderId2).isEqualTo(orderId1);
        assertThat(placeBody1.path("data").path("status").asText()).isEqualTo("PENDING_PAYMENT");
        String txId = "ORDER-TCC-" + orderId1;
        assertThat(tccTransactionRepository.hasSuccess(txId, TccBranch.INVENTORY, TccPhase.TRY)).isTrue();
        assertThat(tccTransactionRepository.hasSuccess(txId, TccBranch.COUPON, TccPhase.TRY)).isTrue();
        assertThat(tccTransactionRepository.hasSuccess(txId, TccBranch.INVENTORY, TccPhase.CONFIRM)).isFalse();

        String payReq = """
            {
              "userId":"U1001",
              "orderId":%d,
              "channel":"ALIPAY",
              "externalNo":"EXT-001"
            }
            """.formatted(orderId1);

        MvcResult payResult1 = mockMvc.perform(post("/api/v1/payments")
                .header("Idempotency-Key", "pay-idem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payReq))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult payResult2 = mockMvc.perform(post("/api/v1/payments")
                .header("Idempotency-Key", "pay-idem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payReq))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode payBody1 = objectMapper.readTree(payResult1.getResponse().getContentAsString());
        JsonNode payBody2 = objectMapper.readTree(payResult2.getResponse().getContentAsString());
        long paymentId1 = payBody1.path("data").path("paymentId").asLong();
        long paymentId2 = payBody2.path("data").path("paymentId").asLong();
        assertThat(paymentId2).isEqualTo(paymentId1);
        assertThat(tccTransactionRepository.hasSuccess(txId, TccBranch.INVENTORY, TccPhase.CONFIRM)).isTrue();
        assertThat(tccTransactionRepository.hasSuccess(txId, TccBranch.COUPON, TccPhase.CONFIRM)).isTrue();
    }
}
