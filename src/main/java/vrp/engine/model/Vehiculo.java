package vrp.engine.model;

import java.time.LocalTime;

public record Vehiculo(
        String id,
        String nombre,
        int capacidad,
        LocalTime horaSalida,
        LocalTime horaFin,
        String depositoSalidaId,
        String depositoLlegadaId) {
}
