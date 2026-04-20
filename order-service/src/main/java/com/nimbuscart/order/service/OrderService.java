package com.nimbuscart.order.service;

import com.nimbuscart.order.client.ProductClient;
import com.nimbuscart.order.client.UserClient;
import com.nimbuscart.order.dto.*;
import com.nimbuscart.order.model.Order;
import com.nimbuscart.order.model.OrderStatus;
import com.nimbuscart.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public OrderResponse createOrder(OrderRequest request) {
        // 1. Validate user exists (sync call)
        userClient.getUserById(request.getUserId());

        // 2. Get product details and check stock (sync call)
        Map<String, Object> product = productClient.getProductById(request.getProductId());
        BigDecimal price = new BigDecimal(product.get("price").toString());
        int stock = (int) product.get("stockQuantity");

        if (stock < request.getQuantity()) {
            throw new RuntimeException("Insufficient stock");
        }

        // 3. Reduce stock
        productClient.reduceStock(request.getProductId(), request.getQuantity());

        // 4. Create order
        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(request.getQuantity()));
        Order order = Order.builder()
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .totalPrice(totalPrice)
                .status(OrderStatus.PENDING)
                .build();
        order = orderRepository.save(order);

        // 5. Publish event to Kafka (async)
        OrderEvent event = new OrderEvent(order.getId(), order.getUserId(),
                order.getProductId(), order.getQuantity(), order.getTotalPrice(), "ORDER_CREATED");
        kafkaTemplate.send("order-created", event);
        log.info("Published ORDER_CREATED event for order {}", order.getId());

        return toResponse(order);
    }

    @KafkaListener(topics = "payment-result", groupId = "order-group")
    public void handlePaymentResult(OrderEvent event) {
        log.info("Received payment result: {} for order {}", event.getEventType(), event.getOrderId());
        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if ("PAYMENT_SUCCESS".equals(event.getEventType())) {
            order.setStatus(OrderStatus.CONFIRMED);
        } else {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
        }
        orderRepository.save(order);
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return toResponse(order);
    }

    public List<OrderResponse> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getUserId(), o.getProductId(),
                o.getQuantity(), o.getTotalPrice(), o.getStatus().name(), o.getCreatedAt());
    }
}
