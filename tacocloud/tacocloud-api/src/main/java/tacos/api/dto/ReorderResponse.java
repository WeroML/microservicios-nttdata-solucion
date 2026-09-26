package tacos.api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TC-24: la respuesta distingue una cotización (confirmed=false, sin orden creada)
 * de una confirmación (confirmed=true, orden nueva creada).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReorderResponse {
    private boolean confirmed;
    private OrderResponse order;
    private List<String> differences;
}
