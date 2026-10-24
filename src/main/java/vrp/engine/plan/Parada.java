package vrp.engine.plan;

import java.time.LocalDateTime;
import vrp.engine.model.Entrega;
import vrp.engine.model.EstadoParada;

public record Parada(
        int orden,
        Entrega entrega,
        EstadoParada estado,
        LocalDateTime eta,
        double distanciaDesdeAnteriorM,
        long duracionDesdeAnteriorSeg,
        String polilineaTramo) {

    /** Copia de la parada con otro orden y otro estado. */
    public Parada conOrdenYEstado(int nuevoOrden, EstadoParada nuevoEstado) {
        return new Parada(nuevoOrden, entrega, nuevoEstado, eta, distanciaDesdeAnteriorM,
                duracionDesdeAnteriorSeg, polilineaTramo);
    }
}
