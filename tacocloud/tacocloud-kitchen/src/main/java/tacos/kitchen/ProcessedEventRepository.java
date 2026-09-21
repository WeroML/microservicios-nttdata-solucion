package tacos.kitchen;

import org.springframework.data.repository.CrudRepository;

public interface ProcessedEventRepository extends CrudRepository<ProcessedEvent, String> {
}
