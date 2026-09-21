package tacos.web.api;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.data.TacoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.HashMap;

@Service
public class TacoOfTheDayService {

    private final TacoRepository tacoRepo;
    private final Clock clock;

    public TacoOfTheDayService(TacoRepository tacoRepo, Clock clock) {
        this.tacoRepo = tacoRepo;
        this.clock = clock;
    }

    public Mono<TacoOfTheDayResponse> getTacoOfTheDay() {
        LocalDate today = LocalDate.now(clock);
        long epochDays = today.toEpochDay();
        
        return tacoRepo.count()
            .flatMap(count -> {
                if (count == 0) {
                    return Mono.empty();
                }
                long index = epochDays % count;
                return tacoRepo.findAll()
                    .sort((t1, t2) -> {
                        if (t1.getId() == null && t2.getId() == null) return 0;
                        if (t1.getId() == null) return -1;
                        if (t2.getId() == null) return 1;
                        return t1.getId().compareTo(t2.getId());
                    })
                    .skip(index)
                    .next()
                    .map(taco -> new TacoOfTheDayResponse(taco, today.toString(), "Seleccionado especialmente para la fecha " + today));
            });
    }

    public static class TacoOfTheDayResponse {
        public final Taco taco;
        public final String date;
        public final String reason;

        public TacoOfTheDayResponse(Taco taco, String date, String reason) {
            this.taco = taco;
            this.date = date;
            this.reason = reason;
        }
    }
}
