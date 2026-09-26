package tacos.actuator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tacos.data.TacoRepository;

// TC-32: el InfoContributor lee un valor ya calculado; nunca hace block().
@Component
public class TacoCountInfoContributor implements InfoContributor {
  private final TacoRepository tacoRepo;
  private final AtomicLong tacoCount = new AtomicLong();

  public TacoCountInfoContributor(TacoRepository tacoRepo) {
    this.tacoRepo = tacoRepo;
  }

  // El scheduler es el borde de ejecución que suscribe.
  @Scheduled(fixedDelayString = "${tacocloud.metrics.gauge-refresh:5000}")
  public void refresh() {
    tacoRepo.count().subscribe(tacoCount::set);
  }

  @Override
  public void contribute(Builder builder) {
    Map<String, Object> tacoMap = new HashMap<String, Object>();
    tacoMap.put("count", tacoCount.get());
    builder.withDetail("taco-stats", tacoMap);
  }
}
