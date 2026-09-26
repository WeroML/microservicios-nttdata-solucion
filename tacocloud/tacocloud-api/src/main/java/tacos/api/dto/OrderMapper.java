package tacos.api.dto;

import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tacos.OrderItem;
import tacos.OrderStatusHistory;
import tacos.TacoOrder;

@Component
public class OrderMapper {

    private final TacoMapper tacoMapper;

    public OrderMapper(TacoMapper tacoMapper) {
        this.tacoMapper = tacoMapper;
    }

    public OrderResponse toResponse(TacoOrder order) {
        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setPlacedAt(order.getPlacedAt());
        resp.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        resp.setDeliveryName(order.getDeliveryName());
        resp.setDeliveryStreet(order.getDeliveryStreet());
        resp.setDeliveryCity(order.getDeliveryCity());
        resp.setDeliveryState(order.getDeliveryState());
        resp.setDeliveryZip(order.getDeliveryZip());
        if (order.getPaymentBrand() != null || order.getPaymentLast4() != null) {
            OrderResponse.PaymentSummary payment = new OrderResponse.PaymentSummary();
            payment.setBrand(order.getPaymentBrand());
            payment.setLast4(order.getPaymentLast4());
            resp.setPayment(payment);
        }
        resp.setDiscountCode(order.getDiscountCode());
        resp.setDiscountAmount(order.getDiscountAmount());
        resp.setCurrency(order.getCurrency());
        resp.setSubtotal(order.getSubtotal());
        resp.setTotal(order.getTotal());
        resp.setItems(order.getItems() == null ? Collections.emptyList()
            : order.getItems().stream().map(this::toItemResponse).collect(Collectors.toList()));
        resp.setStatusHistory(order.getStatusHistory() == null ? Collections.emptyList()
            : order.getStatusHistory().stream().map(this::toHistoryResponse).collect(Collectors.toList()));
        return resp;
    }

    public OrderSummaryResponse toSummary(TacoOrder order) {
        OrderSummaryResponse resp = new OrderSummaryResponse();
        resp.setId(order.getId());
        resp.setPlacedAt(order.getPlacedAt());
        resp.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        resp.setItemCount(order.getItems() == null ? 0
            : order.getItems().stream().mapToInt(OrderItem::getQuantity).sum());
        resp.setCurrency(order.getCurrency());
        resp.setTotal(order.getTotal());
        return resp;
    }

    private OrderResponse.OrderItemResponse toItemResponse(OrderItem item) {
        OrderResponse.OrderItemResponse resp = new OrderResponse.OrderItemResponse();
        resp.setQuantity(item.getQuantity());
        resp.setUnitPriceAtPurchase(item.getUnitPriceAtPurchase());
        resp.setSubtotal(item.getSubtotal());
        resp.setTaco(item.getTaco() == null ? null : tacoMapper.toResponse(item.getTaco()));
        return resp;
    }

    private OrderResponse.StatusHistoryResponse toHistoryResponse(OrderStatusHistory h) {
        OrderResponse.StatusHistoryResponse hr = new OrderResponse.StatusHistoryResponse();
        hr.setStatus(h.getStatus() == null ? null : h.getStatus().name());
        hr.setChangedAt(h.getChangedAt());
        hr.setChangedBy(h.getChangedBy());
        hr.setOrigin(h.getOrigin());
        hr.setReason(h.getReason());
        return hr;
    }
}
