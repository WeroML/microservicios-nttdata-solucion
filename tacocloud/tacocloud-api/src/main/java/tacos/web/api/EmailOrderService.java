package tacos.web.api;

import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import tacos.PaymentMethod;
import tacos.TacoOrder;
import tacos.User;
import tacos.api.dto.OrderCreateRequest;
import tacos.api.dto.TacoRequest;
import tacos.api.error.BusinessRuleException;
import tacos.data.PaymentMethodRepository;
import tacos.data.UserRepository;

/**
 * TC-06: convierte una orden recibida por correo en una sola cadena reactiva,
 * sin subscribe(), sin block() y sin estado mutable compartido.
 *
 * - Usuario y método de pago faltantes producen errores tipados (422 con código).
 * - Los tacos se convierten en el orden en que llegaron (concatMap en OrderDraftFactory)
 *   y la orden sólo se emite cuando todos terminaron.
 * - El orden de ingredientes se conserva tal como vino en el correo; no afecta
 *   precio ni reglas, sólo la presentación.
 * - Un ID de ingrediente desconocido falla indicando cuál fue (UNKNOWN_INGREDIENT).
 */
@Service
public class EmailOrderService {

  public static final String USER_NOT_FOUND = "EMAIL_USER_NOT_FOUND";
  public static final String PAYMENT_METHOD_NOT_FOUND = "PAYMENT_METHOD_NOT_FOUND";

  private final UserRepository userRepo;
  private final PaymentMethodRepository paymentMethodRepo;
  private final OrderDraftFactory draftFactory;

  public EmailOrderService(UserRepository userRepo, PaymentMethodRepository paymentMethodRepo,
                           OrderDraftFactory draftFactory) {
    this.userRepo = userRepo;
    this.paymentMethodRepo = paymentMethodRepo;
    this.draftFactory = draftFactory;
  }

  public Mono<TacoOrder> convertEmailOrderToDomainOrder(Mono<EmailOrder> emailOrder) {
    return emailOrder.flatMap(eOrder ->
        userRepo.findByEmail(eOrder.getEmail())
            .switchIfEmpty(Mono.error(() -> new BusinessRuleException(USER_NOT_FOUND,
                "No user is registered with the email of the order.")))
            .flatMap(user -> paymentMethodRepo.findByUserId(user.getId())
                .next()
                .switchIfEmpty(Mono.error(() -> new BusinessRuleException(PAYMENT_METHOD_NOT_FOUND,
                    "The user has no payment method.")))
                .flatMap(pm -> draftFactory.build(user.getId(), toRequest(eOrder, user, pm)))));
  }

  private static OrderCreateRequest toRequest(EmailOrder eOrder, User user, PaymentMethod pm) {
    OrderCreateRequest request = new OrderCreateRequest();
    request.setDeliveryName(user.getFullname());
    request.setDeliveryStreet(user.getStreet());
    request.setDeliveryCity(user.getCity());
    request.setDeliveryState(user.getState());
    request.setDeliveryZip(user.getZip());
    request.setPaymentMethodId(pm.getId());
    // Las órdenes por correo traen una unidad de cada taco.
    request.setItems(eOrder.getTacos().stream().map(emailTaco -> {
      TacoRequest taco = new TacoRequest();
      taco.setName(emailTaco.getName());
      taco.setIngredientIds(emailTaco.getIngredients());
      OrderCreateRequest.OrderItemRequest item = new OrderCreateRequest.OrderItemRequest();
      item.setTaco(taco);
      item.setQuantity(1);
      return item;
    }).collect(Collectors.toList()));
    return request;
  }

}
