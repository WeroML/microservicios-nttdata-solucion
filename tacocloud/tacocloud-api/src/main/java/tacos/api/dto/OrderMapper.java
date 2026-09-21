package tacos.api.dto;

import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tacos.TacoOrder;
import tacos.Taco;

@Component
public class OrderMapper {

    public OrderResponse toResponse(TacoOrder order) {
        if (order == null) return null;
        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setPlacedAt(order.getPlacedAt());
        resp.setDeliveryName(order.getDeliveryName());
        resp.setDeliveryStreet(order.getDeliveryStreet());
        resp.setDeliveryCity(order.getDeliveryCity());
        resp.setDeliveryState(order.getDeliveryState());
        resp.setDeliveryZip(order.getDeliveryZip());
        resp.setDiscountCode(order.getDiscountCode());
        resp.setDiscountAmount(order.getDiscountAmount());
        
        resp.setTotal(order.getTotal());
        if (order.getStatus() != null) {
            resp.setStatus(order.getStatus().name());
        }
        if (order.getStatusHistory() != null) {
            java.util.List<OrderResponse.StatusHistoryResponse> history = new java.util.ArrayList<>();
            for (tacos.OrderStatusHistory h : order.getStatusHistory()) {
                OrderResponse.StatusHistoryResponse hr = new OrderResponse.StatusHistoryResponse();
                hr.setStatus(h.getStatus().name());
                hr.setChangedAt(h.getChangedAt());
                hr.setChangedBy(h.getChangedBy());
                hr.setReason(h.getReason());
                history.add(hr);
            }
            resp.setStatusHistory(history);
        }
        if (order.getItems() != null) {
            resp.setItems(order.getItems().stream().map(this::toItemResponse).collect(Collectors.toList()));
        }
        return resp;
    }

    private OrderResponse.OrderItemResponse toItemResponse(tacos.OrderItem item) {
        if (item == null) return null;
        OrderResponse.OrderItemResponse resp = new OrderResponse.OrderItemResponse();
        resp.setQuantity(item.getQuantity());
        resp.setUnitPriceAtPurchase(item.getUnitPriceAtPurchase());
        resp.setSubtotal(item.getSubtotal());
        resp.setTaco(toTacoResponse(item.getTaco()));
        return resp;
    }

    private OrderResponse.TacoResponse toTacoResponse(Taco taco) {
        if (taco == null) return null;
        OrderResponse.TacoResponse resp = new OrderResponse.TacoResponse();
        resp.setId(taco.getId());
        resp.setName(taco.getName());
        resp.setCreatedAt(taco.getCreatedAt());
        if (taco.getIngredients() != null) {
            resp.setIngredients(taco.getIngredients().stream().map(i -> {
                IngredientResponse ir = new IngredientResponse();
                ir.setId(i.getId());
                ir.setName(i.getName());
                ir.setType(i.getType());
                ir.setUnitPrice(i.getUnitPrice());
                ir.setAvailable(i.isAvailable());
                return ir;
            }).collect(Collectors.toList()));
        }
        return resp;
    }
}
