package tacos.web.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * TC-29: las transacciones de MongoDB sólo funcionan en un replica set (versión 4.0+).
 * En desarrollo, el Mongo embebido arranca como replica set de un nodo
 * (spring.mongodb.embedded.storage.repl-set-name en application.yml); en las pruebas
 * con Testcontainers, MongoDBContainer ya es un replica set.
 */
@Configuration
@EnableTransactionManagement
public class MongoTxConfig {

    @Bean
    public ReactiveTransactionManager transactionManager(ReactiveMongoDatabaseFactory dbFactory) {
        return new ReactiveMongoTransactionManager(dbFactory);
    }
}
