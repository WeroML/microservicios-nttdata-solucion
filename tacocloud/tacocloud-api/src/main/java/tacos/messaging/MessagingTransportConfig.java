package tacos.messaging;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import tacos.messaging.contract.OrderMessagingService;

/**
 * TC-28: el transporte se elige con tacocloud.messaging.transport=noop|jms|rabbit|kafka
 * (sin editar el POM ni recompilar). La aplicación no arranca, con un mensaje claro,
 * si el valor falta o es desconocido, o si no queda activo exactamente un adaptador.
 * La validación corre antes de crear cualquier bean.
 */
@Configuration
public class MessagingTransportConfig {

    public static final String PROPERTY = "tacocloud.messaging.transport";
    public static final List<String> TRANSPORTS = Arrays.asList("noop", "jms", "rabbit", "kafka");

    @Bean
    public static BeanFactoryPostProcessor messagingTransportValidator(Environment environment) {
        return beanFactory -> {
            String transport = environment.getProperty(PROPERTY, "");
            if (!TRANSPORTS.contains(transport)) {
                throw new IllegalStateException(PROPERTY + " must be one of " + TRANSPORTS
                    + " but was '" + transport + "'");
            }
            String[] adapters = beanFactory.getBeanNamesForType(OrderMessagingService.class, true, false);
            if (adapters.length != 1) {
                throw new IllegalStateException("Exactly one OrderMessagingService must be active for "
                    + PROPERTY + "=" + transport + " but found " + Arrays.toString(adapters));
            }
        };
    }
}
