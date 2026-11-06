package vrp.engine.service;

import java.time.LocalDateTime;
import java.util.List;
import vrp.engine.model.PosicionVehiculo;

public record CambiosJornada(
        LocalDateTime ahora,
        List<PosicionVehiculo> posiciones,
        List<ModificacionEntrega> modificaciones,
        List<String> devolverADeposito,
        List<Reprogramacion> reprogramar) {

    public CambiosJornada {
        posiciones = posiciones == null ? List.of() : List.copyOf(posiciones);
        modificaciones = modificaciones == null ? List.of() : List.copyOf(modificaciones);
        devolverADeposito = devolverADeposito == null ? List.of() : List.copyOf(devolverADeposito);
        reprogramar = reprogramar == null ? List.of() : List.copyOf(reprogramar);
    }
}
