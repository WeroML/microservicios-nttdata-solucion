package tacos.validation;

import java.util.List;

import tacos.Taco;

/**
 * TC-18: cada regla es una especificación independiente del diseño.
 * Recibe un taco con ingredientes ya resueltos del catálogo y retorna
 * violaciones; no lanza excepciones ni consulta repositorios.
 */
public interface TacoRule {
    List<RuleViolation> validate(Taco taco);
}
