package vrp.engine.plan;

import java.time.LocalDateTime;
import java.util.List;
import vrp.engine.model.Coordenada;
import vrp.engine.model.Vehiculo;

public record RutaVehiculo(
        Vehiculo vehiculo,
        Coordenada origen,
        Coordenada posicionActual,
        Coordenada destino,
        LocalDateTime salida,
        LocalDateTime regreso,
        List<Parada> paradas,
        double distanciaRegresoM,
        long duracionRegresoSeg,
        String polilineaRegreso,
        String polilinea,
        double distanciaTotalM,
        long duracionTotalSeg) {

    public RutaVehiculo {
        paradas = paradas == null ? List.of() : List.copyOf(paradas);
    }

    /** Copia de la ruta con otra lista de paradas; no recalcula los totales. */
    public RutaVehiculo conParadas(List<Parada> nuevasParadas) {
        return new RutaVehiculo(vehiculo, origen, posicionActual, destino, salida, regreso, nuevasParadas,
                distanciaRegresoM, duracionRegresoSeg, polilineaRegreso, polilinea, distanciaTotalM, duracionTotalSeg);
    }
}
