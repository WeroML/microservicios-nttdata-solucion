package tacos.kitchen.ui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import tacos.kitchen.KitchenTicketRepository;

/**
 * TC-26: KitchenUI. Muestra la cola (API), permite reclamar la siguiente orden
 * y avanzar el estado de las órdenes en curso (vista construida con eventos).
 */
@Controller
public class KitchenController {

    // Siguiente estado de cocina; la validación real vive en OrderStateService de la API.
    static final Map<String, String> NEXT_STATUS = new HashMap<>();

    static {
        NEXT_STATUS.put("ACCEPTED", "PREPARING");
        NEXT_STATUS.put("PREPARING", "READY");
        NEXT_STATUS.put("READY", "OUT_FOR_DELIVERY");
        NEXT_STATUS.put("OUT_FOR_DELIVERY", "DELIVERED");
    }

    private final TacoCloudApiClient api;
    private final KitchenTicketRepository tickets;

    public KitchenController(TacoCloudApiClient api, KitchenTicketRepository tickets) {
        this.api = api;
        this.tickets = tickets;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/kitchen";
    }

    @GetMapping("/kitchen")
    public String kitchen(Model model) {
        try {
            model.addAttribute("queue", api.queue());
        } catch (RestClientException e) {
            model.addAttribute("queue", java.util.Collections.emptyList());
            model.addAttribute("error", "The Taco Cloud API is not reachable.");
        }
        model.addAttribute("inProgress", tickets.findByStatusInOrderByReceivedAtAsc(
            Arrays.asList("ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY")));
        model.addAttribute("nextStatus", NEXT_STATUS);
        return "kitchen";
    }

    @PostMapping("/kitchen/claim")
    public String claim(RedirectAttributes redirect) {
        try {
            Map<String, Object> claimed = api.claimNext();
            redirect.addFlashAttribute("message", claimed == null
                ? "The queue is empty."
                : "Claimed order " + claimed.get("id") + " (ETA " + claimed.get("estimatedPrepMinutes") + " min).");
        } catch (RestClientException e) {
            redirect.addFlashAttribute("error", "Could not claim an order.");
        }
        return "redirect:/kitchen";
    }

    @PostMapping("/kitchen/orders/{orderId}/advance/{status}")
    public String advance(@PathVariable String orderId, @PathVariable String status, RedirectAttributes redirect) {
        try {
            api.changeStatus(orderId, status);
            redirect.addFlashAttribute("message", "Order " + orderId + " -> " + status);
        } catch (RestClientException e) {
            redirect.addFlashAttribute("error", "Could not change the status of order " + orderId + ".");
        }
        return "redirect:/kitchen";
    }
}
