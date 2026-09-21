package tacos.web.api;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.TacoOrder;
import tacos.Taco;
import tacos.api.dto.OrderCreateRequest;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import java.util.Date;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final tacos.data.TacoRepository tacoRepo;
    private final tacos.data.OutboxEventRepository outboxRepo;
    private final IngredientRepository ingredientRepo;
    private final tacos.discount.DiscountService discountService;
    private final tacos.validation.TacoValidatorService tacoValidatorService;
    private final tacos.classification.ClassificationService classificationService;
    private final TacoMetricsService metricsService;

    public OrderService(OrderRepository orderRepo, 
                        tacos.data.TacoRepository tacoRepo, 
                        tacos.data.OutboxEventRepository outboxRepo,
                        IngredientRepository ingredientRepo, 
                        tacos.discount.DiscountService discountService,
                        tacos.validation.TacoValidatorService tacoValidatorService,
                        tacos.classification.ClassificationService classificationService,
                        TacoMetricsService metricsService) {
        this.orderRepo = orderRepo;
        this.tacoRepo = tacoRepo;
        this.outboxRepo = outboxRepo;
        this.ingredientRepo = ingredientRepo;
        this.discountService = discountService;
        this.tacoValidatorService = tacoValidatorService;
        this.classificationService = classificationService;
        this.metricsService = metricsService;
    }

    public Mono<TacoOrder> createOrderFromRequest(OrderCreateRequest request) {
        TacoOrder order = new TacoOrder();
        order.setDeliveryName(request.getDeliveryName());
        order.setDeliveryStreet(request.getDeliveryStreet());
        order.setDeliveryCity(request.getDeliveryCity());
        order.setDeliveryState(request.getDeliveryState());
        order.setDeliveryZip(request.getDeliveryZip());
        order.setPaymentMethodId(request.getPaymentMethodId());
        order.setPlacedAt(new Date());

        return Flux.fromIterable(request.getItems())
            .concatMap(itemReq -> {
                tacos.api.dto.OrderCreateRequest.TacoRequest tacoReq = itemReq.getTaco();
                return Flux.fromIterable(tacoReq.getIngredients())
                    .concatMap(ingredientId -> 
                        ingredientRepo.findById(ingredientId)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Ingredient not found: " + ingredientId)))
                    )
                    .collectList()
                    .map(ingredients -> {
                        Taco taco = new Taco();
                        taco.setName(tacoReq.getName());
                        taco.setIngredients(ingredients);
                        java.util.List<String> errors = tacoValidatorService.validate(taco);
                        if (!errors.isEmpty()) {
                            throw new IllegalArgumentException("Invalid taco design: " + String.join(", ", errors));
                        }
                        
                        tacos.classification.ClassificationService.TacoClassification classification = classificationService.classify(taco);
                        taco.setDietaryTags(classification.dietaryTags);
                        taco.setAllergens(classification.allergens);
                        taco.setSpiceLevel(classification.spiceLevel);
                        
                        java.math.BigDecimal tacoPrice = ingredients.stream()
                            .map(i -> i.getUnitPrice() != null ? i.getUnitPrice() : java.math.BigDecimal.ZERO)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                            
                        tacos.OrderItem item = new tacos.OrderItem();
                        item.setTaco(taco);
                        
                        int quantity = itemReq.getQuantity() != null ? itemReq.getQuantity() : 1;
                        if (quantity <= 0 || quantity > 100) {
                            throw new IllegalArgumentException("Invalid quantity: " + quantity);
                        }
                        item.setQuantity(quantity);
                        item.setUnitPriceAtPurchase(tacoPrice);
                        item.setSubtotal(tacoPrice.multiply(java.math.BigDecimal.valueOf(quantity)).setScale(2, java.math.RoundingMode.HALF_UP));
                        
                        return item;
                    });
            })
            .collectList()
            .map(items -> {
                java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
                for (tacos.OrderItem item : items) {
                    order.addItem(item);
                    subtotal = subtotal.add(item.getSubtotal());
                }
                
                if (request.getDiscountCode() != null && !request.getDiscountCode().isEmpty()) {
                    tacos.discount.DiscountService.DiscountResult discountResult = discountService.applyDiscount(request.getDiscountCode(), subtotal);
                    if ("APPLIED".equals(discountResult.status)) {
                        order.setDiscountCode(discountResult.appliedCode);
                        order.setDiscountAmount(discountResult.discountApplied);
                        order.setTotal(discountResult.finalTotal);
                        metricsService.recordCouponApplied();
                    } else {
                        // Requirements: "Expirado, no iniciado, mínimo no alcanzado y desconocido tienen respuesta definida."
                        // We will throw IllegalArgumentException so it's mapped to 422
                        throw new IllegalArgumentException(discountResult.message);
                    }
                } else {
                    order.setTotal(subtotal);
                }
                return order;
            });
    }
}
