package tacos.kitchen;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

public interface KitchenTicketRepository extends CrudRepository<KitchenTicket, String> {
    List<KitchenTicket> findByStatusInOrderByReceivedAtAsc(List<String> statuses);
}
