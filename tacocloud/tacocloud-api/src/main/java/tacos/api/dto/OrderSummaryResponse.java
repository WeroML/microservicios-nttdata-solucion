package tacos.api.dto;

import java.math.BigDecimal;
import java.util.Date;

import lombok.Data;

// TC-23: resumen para el historial paginado.
@Data
public class OrderSummaryResponse {
    private String id;
    private Date placedAt;
    private String status;
    private int itemCount;
    private String currency;
    private BigDecimal total;
}
