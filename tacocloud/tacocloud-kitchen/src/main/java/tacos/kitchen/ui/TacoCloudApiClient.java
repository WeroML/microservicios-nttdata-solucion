package tacos.kitchen.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * TC-26: cliente de la API de cocina. La identidad KITCHEN (y con ella el cookId)
 * viene de configuración del proceso vía variables de ambiente, nunca de Git.
 */
@Component
public class TacoCloudApiClient {

    private final RestTemplate rest;
    private final String baseUrl;
    private final String username;
    private final String password;

    public TacoCloudApiClient(RestTemplate rest,
                              @Value("${tacocloud.api.base-url}") String baseUrl,
                              @Value("${tacocloud.api.username:}") String username,
                              @Value("${tacocloud.api.password:}") String password) {
        this.rest = rest;
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queue() {
        ResponseEntity<Map[]> response = rest.exchange(baseUrl + "/api/v1/kitchen/queue", HttpMethod.GET,
            new HttpEntity<>(headers()), Map[].class);
        Map[] body = response.getBody();
        return body == null ? Collections.emptyList() : (List<Map<String, Object>>) (List<?>) Arrays.asList(body);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> claimNext() {
        ResponseEntity<Map> response = rest.exchange(baseUrl + "/api/v1/kitchen/orders/claim", HttpMethod.POST,
            new HttpEntity<>(headers()), Map.class);
        return response.getBody();
    }

    public void changeStatus(String orderId, String status) {
        rest.exchange(baseUrl + "/api/v1/orders/" + orderId + "/status", HttpMethod.PATCH,
            new HttpEntity<>(Collections.singletonMap("status", status), headers()), Map.class);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
