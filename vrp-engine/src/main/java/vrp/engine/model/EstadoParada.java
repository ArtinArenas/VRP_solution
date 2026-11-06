package vrp.engine.model;

public enum EstadoParada {
    PENDIENTE,
    EN_CAMINO,
    ENTREGADO,
    REPROGRAMADO,
    CANCELADO,
    DEVOLVER_A_DEPOSITO;

    /** Un estado cerrado es definitivo: la parada ya no puede reabrirse. */
    public boolean cerrado() {
        return this == ENTREGADO || this == CANCELADO || this == DEVOLVER_A_DEPOSITO;
    }
}
