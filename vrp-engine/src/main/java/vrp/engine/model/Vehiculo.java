package vrp.engine.model;

import java.time.LocalTime;

public record Vehiculo(
        String id,
        String nombre,
        int capacidad,
        LocalTime horaSalida,
        LocalTime horaFin,
        String depositoSalidaId,
        String depositoLlegadaId,
        int maxViajes) {

    public Vehiculo {
        if (maxViajes <= 0) maxViajes = 1;
    }

    /**
     * Retorna la hora de salida más temprana (horaSalida).
     * En multi-viaje, la primera salida es a esta hora.
     */
    public LocalTime horaSalidaMasTemprana() {
        return horaSalida;
    }
}
