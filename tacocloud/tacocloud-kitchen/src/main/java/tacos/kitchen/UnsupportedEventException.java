package tacos.kitchen;

// TC-30: error permanente (tipo o versión desconocidos). No se reintenta: va directo a la DLQ.
public class UnsupportedEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedEventException(String message) {
        super(message);
    }
}
