package com.nimbuscart.payment.service;

import com.nimbuscart.payment.dto.OrderEvent;
import com.nimbuscart.payment.model.Payment;
import com.nimbuscart.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final Random random = new Random();

    @KafkaListener(topics = "order-created", groupId = "payment-group")
    public void handleOrderCreated(OrderEvent event) {
        log.info("Received ORDER_CREATED event for order {}", event.getOrderId());

        // Mock payment processing (80% success rate)
        boolean success = random.nextInt(100) < 80;

        Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .amount(event.getTotalPrice())
                .status(success ? "SUCCESS" : "FAILED")
                .build();
        paymentRepository.save(payment);

        // Publish payment result
        String resultType = success ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED";
        event.setEventType(resultType);
        kafkaTemplate.send("payment-result", event);
        log.info("Published {} for order {}", resultType, event.getOrderId());
    }
}
