package tacos.validation;

import tacos.Taco;
import java.util.List;

public interface TacoRule {
    List<String> validate(Taco taco);
}
