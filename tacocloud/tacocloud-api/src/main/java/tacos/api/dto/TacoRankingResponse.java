package tacos.api.dto;

import java.math.BigDecimal;
import java.util.Map;

import lombok.Data;

// TC-22: promedio con 2 decimales (HALF_UP), cantidad de votos y distribución 1..5.
@Data
public class TacoRankingResponse {
    private String tacoId;
    private String tacoName;
    private BigDecimal average;
    private long votes;
    private Map<Integer, Long> distribution;
}
