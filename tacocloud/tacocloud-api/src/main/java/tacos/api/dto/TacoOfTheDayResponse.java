package tacos.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacoOfTheDayResponse {
    private TacoResponse taco;
    private String date;
    private String reason;
}
