package tacos;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.flapdoodle.embed.mongo.config.MongoCmdOptions;
import de.flapdoodle.embed.mongo.config.MongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.Storage;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;

/**
 * TC-29: Mongo embebido como replica set de un nodo (necesario para transacciones).
 * Spring Boot 2.5 arranca mongod sin journal y un replica set lo exige, por eso
 * esta configuración reemplaza la de Boot. El replica set lo inicia
 * EmbeddedMongoReplicaSetInitializer.
 */
@Configuration
@ConditionalOnClass(MongodConfig.class)
@ConditionalOnProperty("spring.mongodb.embedded.storage.repl-set-name")
public class EmbeddedMongoReplicaSetConfig {

  @Bean
  public MongodConfig embeddedMongoConfiguration(
      @Value("${spring.mongodb.embedded.storage.repl-set-name}") String replicaSetName) throws IOException {
    return MongodConfig.builder()
        .version(Version.V4_0_12)
        .net(new Net("localhost", Network.getFreeServerPort(), Network.localhostIsIPv6()))
        .replication(new Storage(null, replicaSetName, 0))
        .cmdOptions(MongoCmdOptions.builder().useNoJournal(false).build())
        .build();
  }
}
