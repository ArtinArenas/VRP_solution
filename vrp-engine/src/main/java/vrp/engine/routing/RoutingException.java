package vrp.engine.routing;

public class RoutingException extends RuntimeException {

    public RoutingException(String mensaje) {
        super(mensaje);
    }

    public RoutingException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
