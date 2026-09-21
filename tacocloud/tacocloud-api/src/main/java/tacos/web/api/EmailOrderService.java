package tacos.web.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.TacoOrder;
import tacos.PaymentMethod;
import tacos.Taco;
import tacos.User;
import tacos.data.IngredientRepository;
import tacos.data.PaymentMethodRepository;
import tacos.data.UserRepository;
import tacos.web.api.EmailOrder.EmailTaco;

@Service
public class EmailOrderService {

  private UserRepository userRepo;
  private IngredientRepository ingredientRepo;
  private PaymentMethodRepository paymentMethodRepo;
  private tacos.validation.TacoValidatorService tacoValidatorService;
  private tacos.classification.ClassificationService classificationService;

  public EmailOrderService(UserRepository userRepo, IngredientRepository ingredientRepo,
      PaymentMethodRepository paymentMethodRepo,
      tacos.validation.TacoValidatorService tacoValidatorService,
      tacos.classification.ClassificationService classificationService) {
    this.userRepo = userRepo;
    this.ingredientRepo = ingredientRepo;
    this.paymentMethodRepo = paymentMethodRepo;
    this.tacoValidatorService = tacoValidatorService;
    this.classificationService = classificationService;
  }

  public Mono<TacoOrder> convertEmailOrderToDomainOrder(Mono<EmailOrder> emailOrder) {
    return emailOrder.flatMap(eOrder -> {
      Mono<User> userMono = userRepo.findByEmail(eOrder.getEmail())
          .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found for email: " + eOrder.getEmail())));

      Mono<PaymentMethod> paymentMono = userMono.flatMap(user -> 
          paymentMethodRepo.findByUserId(user.getId())
              .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment method not found for user: " + user.getId())))
      );

      return Mono.zip(userMono, paymentMono).flatMap(tuple -> {
        User user = tuple.getT1();
        PaymentMethod paymentMethod = tuple.getT2();
        TacoOrder order = new TacoOrder();
        order.setUser(user);
        order.setPaymentMethodId(paymentMethod.getId());
        order.setDeliveryName(user.getFullname());
        order.setDeliveryStreet(user.getStreet());
        order.setDeliveryCity(user.getCity());
        order.setDeliveryState(user.getState());
        order.setDeliveryZip(user.getZip());
        order.setPlacedAt(new Date());

        return Flux.fromIterable(eOrder.getTacos())
            .concatMap(emailTaco -> {
              return Flux.fromIterable(emailTaco.getIngredients())
                  .concatMap(ingredientId -> 
                      ingredientRepo.findById(ingredientId)
                          .switchIfEmpty(Mono.error(new IllegalArgumentException("Ingredient not found: " + ingredientId)))
                  )
                  .collectList()
                  .map(ingredients -> {
                    Taco taco = new Taco();
                    taco.setName(emailTaco.getName());
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
                    item.setQuantity(1); // Email orders only have 1 of each taco implicitly
                    item.setUnitPriceAtPurchase(tacoPrice);
                    item.setSubtotal(tacoPrice);
                    
                    return item;
                  });
            })
            .collectList()
            .map(items -> {
              java.math.BigDecimal total = java.math.BigDecimal.ZERO;
              for (tacos.OrderItem item : items) {
                  order.addItem(item);
                  total = total.add(item.getSubtotal());
              }
              order.setTotal(total);
              return order;
            });
      });
    });
  }

}
