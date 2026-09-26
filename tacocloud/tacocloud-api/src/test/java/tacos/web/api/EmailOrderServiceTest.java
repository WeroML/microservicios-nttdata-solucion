package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.OrderItem;
import tacos.User;
import tacos.api.error.ApiException;
import tacos.data.PaymentMethodRepository;
import tacos.data.UserRepository;
import tacos.testsupport.Fixtures;

// TC-06: conversión determinista de órdenes por correo, sin subscribe ni estado compartido.
class EmailOrderServiceTest {

    private UserRepository userRepo;
    private PaymentMethodRepository paymentRepo;
    private EmailOrderService service;
    private User craig;

    @BeforeEach
    void setUp() {
        Map<String, Ingredient> catalog = Fixtures.catalog();
        userRepo = mock(UserRepository.class);
        paymentRepo = Fixtures.paymentRepo();
        service = new EmailOrderService(userRepo, paymentRepo, new OrderDraftFactory(
            Fixtures.designService(Fixtures.ingredientRepo(catalog)), Fixtures.pricing(),
            Fixtures.discountService(Fixtures.CLOCK), paymentRepo, Fixtures.CLOCK));
        craig = new User("habuma", "{bcrypt}x", "Craig Walls", "123 North Street", "Cross Roads", "TX",
            "76227", "123", "craig@habuma.com");
        craig.setId(Fixtures.USER_ID);
        when(userRepo.findByEmail(anyString())).thenReturn(Mono.empty());
        when(userRepo.findByEmail("craig@habuma.com")).thenReturn(Mono.just(craig));
    }

    private static EmailOrder emailOrder(String email, EmailOrder.EmailTaco... tacos) {
        EmailOrder order = new EmailOrder();
        order.setEmail(email);
        order.setTacos(Arrays.asList(tacos));
        return order;
    }

    private static EmailOrder.EmailTaco taco(String name, String... ingredients) {
        EmailOrder.EmailTaco taco = new EmailOrder.EmailTaco();
        taco.setName(name);
        taco.setIngredients(Arrays.asList(ingredients));
        return taco;
    }

    @Test
    void convertsSeveralTacosInOrderWithAllIngredients() {
        EmailOrder order = emailOrder("craig@habuma.com",
            taco("First taco", "FLTO", "GRBF", "CHED"),
            taco("Second taco", "COTO", "CARN", "SLSA", "TMTO"),
            taco("Third taco", "COTO", "TMTO"));

        StepVerifier.create(service.convertEmailOrderToDomainOrder(Mono.just(order)))
            .assertNext(o -> {
                assertThat(o.getUserId()).isEqualTo(Fixtures.USER_ID);
                assertThat(o.getPaymentMethodId()).isEqualTo(Fixtures.PAYMENT_ID);
                assertThat(o.getDeliveryName()).isEqualTo("Craig Walls");
                assertThat(o.getItems()).extracting(i -> i.getTaco().getName())
                    .containsExactly("First taco", "Second taco", "Third taco");
                assertThat(o.getItems().get(1).getTaco().getIngredients()).extracting(Ingredient::getId)
                    .containsExactly("COTO", "CARN", "SLSA", "TMTO");
                assertThat(o.getItems()).extracting(OrderItem::getQuantity).containsOnly(1);
            })
            .verifyComplete();
    }

    @Test
    void unknownIngredientFailsNamingTheIngredient() {
        EmailOrder order = emailOrder("craig@habuma.com", taco("Mystery", "FLTO", "XXXX"));

        StepVerifier.create(service.convertEmailOrderToDomainOrder(Mono.just(order)))
            .expectErrorSatisfies(e -> {
                assertThat(((ApiException) e).getCode()).isEqualTo("UNKNOWN_INGREDIENT");
                assertThat(e.getMessage()).contains("XXXX");
            })
            .verify();
    }

    @Test
    void missingUserIsAControlledError() {
        StepVerifier.create(service.convertEmailOrderToDomainOrder(
                Mono.just(emailOrder("nobody@tacocloud.test", taco("Classic", "FLTO", "GRBF")))))
            .expectErrorSatisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(EmailOrderService.USER_NOT_FOUND))
            .verify();
    }

    @Test
    void missingPaymentMethodIsAControlledError() {
        when(paymentRepo.findByUserId(Fixtures.USER_ID)).thenReturn(Flux.empty());

        StepVerifier.create(service.convertEmailOrderToDomainOrder(
                Mono.just(emailOrder("craig@habuma.com", taco("Classic", "FLTO", "GRBF")))))
            .expectErrorSatisfies(e -> assertThat(((ApiException) e).getCode())
                .isEqualTo(EmailOrderService.PAYMENT_METHOD_NOT_FOUND))
            .verify();
    }
}
