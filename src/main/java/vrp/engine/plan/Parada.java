package vrp.engine.plan;

import java.time.LocalDateTime;
import vrp.engine.model.Deposito;
import vrp.engine.model.Entrega;
import vrp.engine.model.EstadoParada;

public record Parada(
        int orden,
        Entrega entrega,
        Deposito deposito,
        TipoParada tipo,
        EstadoParada estado,
        LocalDateTime eta,
        double distanciaDesdeAnteriorM,
        long duracionDesdeAnteriorSeg,
        String polilineaTramo) {

    public Parada {
        if (tipo == null) tipo = TipoParada.ENTREGA;
        if (entrega == null && deposito == null) {
            throw new IllegalArgumentException("Parada debe tener entrega o depósito");
        }
    }

    /** Parada de entrega. */
    public static Parada entrega(int orden, Entrega entrega, EstadoParada estado,
            LocalDateTime eta, double distanciaM, long duracionSeg, String polilinea) {
        return new Parada(orden, entrega, null, TipoParada.ENTREGA, estado, eta, distanciaM, duracionSeg, polilinea);
    }

    /** Parada de depósito (carga/descarga). */
    public static Parada deposito(int orden, Deposito deposito, EstadoParada estado,
            LocalDateTime eta, double distanciaM, long duracionSeg, String polilinea) {
        return new Parada(orden, null, deposito, TipoParada.DEPOSITO, estado, eta, distanciaM, duracionSeg, polilinea);
    }

    /** Copia de la parada con otro orden y otro estado. */
    public Parada conOrdenYEstado(int nuevoOrden, EstadoParada nuevoEstado) {
        return new Parada(nuevoOrden, entrega, deposito, tipo, nuevoEstado, eta, distanciaDesdeAnteriorM,
                duracionDesdeAnteriorSeg, polilineaTramo);
    }

    /** ID de la entidad (entrega o depósito). */
    public String entidadId() {
        return entrega != null ? entrega.id() : deposito.id();
    }

    /** Indica si es una parada de depósito. */
    public boolean esDeposito() {
        return tipo == TipoParada.DEPOSITO;
    }
}
