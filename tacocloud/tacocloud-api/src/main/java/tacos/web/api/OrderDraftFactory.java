package tacos.web.api;

import java.time.Clock;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.OrderItem;
import tacos.PaymentMethod;
import tacos.TacoOrder;
import tacos.api.dto.OrderCreateRequest;
import tacos.api.error.BusinessRuleException;
import tacos.data.PaymentMethodRepository;
import tacos.discount.DiscountService;
import tacos.pricing.PricingService;
import tacos.validation.TacoDesignService;

/**
 * Construye una orden (sin guardarla) a partir de un request: resuelve y valida
 * cada taco, calcula precios en el servidor, aplica el cupón y toma el snapshot
 * no sensible del método de pago. Es el mismo camino para crear, cotizar,
 * orden por correo y reordenar.
 */
@Component
public class OrderDraftFactory {

    private final TacoDesignService designService;
    private final PricingService pricingService;
    private final DiscountService discountService;
    private final PaymentMethodRepository paymentMethodRepo;
    private final Clock clock;

    public OrderDraftFactory(TacoDesignService designService, PricingService pricingService,
                             DiscountService discountService, PaymentMethodRepository paymentMethodRepo,
                             Clock clock) {
        this.designService = designService;
        this.pricingService = pricingService;
        this.discountService = discountService;
        this.paymentMethodRepo = paymentMethodRepo;
        this.clock = clock;
    }

    public Mono<TacoOrder> build(String userId, OrderCreateRequest request) {
        // TC-12: el método de pago debe ser un token del mismo usuario.
        Mono<PaymentMethod> paymentMethod = paymentMethodRepo.findByIdAndUserId(request.getPaymentMethodId(), userId)
            .switchIfEmpty(Mono.error(() -> new BusinessRuleException("PAYMENT_METHOD_INVALID",
                "The payment method does not exist or does not belong to the user.")));

        // TC-06/TC-14: cada taco se diseña y se cotiza en orden; la orden no se arma
        // hasta que todas las líneas terminaron.
        Mono<List<OrderItem>> items = Flux.fromIterable(request.getItems())
            .concatMap(item -> designService.design(item.getTaco())
                .map(taco -> pricingService.priceLine(taco, item.getQuantity())))
            .collectList();

        return Mono.zip(paymentMethod, items)
            .map(t -> assemble(userId, request, t.getT1(), t.getT2()));
    }

    private TacoOrder assemble(String userId, OrderCreateRequest request, PaymentMethod pm, List<OrderItem> items) {
        TacoOrder order = new TacoOrder();
        order.setUserId(userId);
        order.setPlacedAt(Date.from(clock.instant()));
        order.setDeliveryName(request.getDeliveryName());
        order.setDeliveryStreet(request.getDeliveryStreet());
        order.setDeliveryCity(request.getDeliveryCity());
        order.setDeliveryState(request.getDeliveryState());
        order.setDeliveryZip(request.getDeliveryZip());
        order.setPaymentMethodId(pm.getId());
        order.setPaymentBrand(pm.getBrand());
        order.setPaymentLast4(pm.getLast4());
        order.setCurrency(pricingService.currency());
        items.forEach(order::addItem);

        order.setSubtotal(pricingService.subtotal(items));
        order.setTotal(order.getSubtotal());
        String code = request.getDiscountCode();
        if (code != null && !code.trim().isEmpty()) {
            DiscountService.DiscountResult discount = discountService.applyDiscount(code, order.getSubtotal());
            if (!discount.isApplied()) {
                throw new BusinessRuleException(discount.publicResult(), "The discount code cannot be applied.");
            }
            order.setDiscountCode(discount.appliedCode);
            order.setDiscountAmount(discount.discount);
            order.setTotal(discount.total);
        }
        return order;
    }
}
