package tacos;

import java.time.Duration;

import org.bson.Document;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;

import de.flapdoodle.embed.mongo.MongodExecutable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * TC-29: las transacciones de Mongo requieren replica set. Spring Boot 2.5 arranca
 * el Mongo embebido con repl-set-name pero no inicia el replica set; este componente
 * ejecuta replSetInitiate apenas arranca mongod y espera a que sea PRIMARY, antes de
 * que la aplicación lo use. Borde de arranque: aquí sí se bloquea.
 */
@Component
@ConditionalOnClass(MongodExecutable.class)
@ConditionalOnProperty("spring.mongodb.embedded.storage.repl-set-name")
public class EmbeddedMongoReplicaSetInitializer implements BeanPostProcessor, EnvironmentAware {

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof MongodExecutable) {
      initiate(environment.getProperty("local.mongo.port"),
          environment.getProperty("spring.mongodb.embedded.storage.repl-set-name"));
    }
    return bean;
  }

  private static void initiate(String port, String replicaSetName) {
    String host = "localhost:" + port;
    Document config = new Document("_id", replicaSetName)
        .append("members", java.util.Collections.singletonList(new Document("_id", 0).append("host", host)));
    try (MongoClient client = MongoClients.create("mongodb://" + host + "/?directConnection=true")) {
      Mono.from(client.getDatabase("admin").runCommand(new Document("replSetInitiate", config))).block();
      Mono.defer(() -> Mono.from(client.getDatabase("admin").runCommand(new Document("isMaster", 1))))
          .filter(result -> Boolean.TRUE.equals(result.getBoolean("ismaster")))
          .repeatWhenEmpty(30, attempts -> attempts.delayElements(Duration.ofMillis(200)))
          .retryWhen(Retry.fixedDelay(30, Duration.ofMillis(200)))
          .block(Duration.ofSeconds(30));
    }
  }
}
