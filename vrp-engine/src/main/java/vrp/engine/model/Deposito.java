package vrp.engine.model;

import java.time.LocalTime;

public record Deposito(
        String id,
        String nombre,
        Coordenada coordenada,
        LocalTime ventanaDesde,
        LocalTime ventanaHasta,
        int estadiaMin) {

    public Deposito {
        if (coordenada == null) throw new IllegalArgumentException("Coordenada es obligatoria para Deposito");
        if (ventanaDesde == null) ventanaDesde = LocalTime.MIN;
        if (ventanaHasta == null) ventanaHasta = LocalTime.MAX;
        if (estadiaMin < 0) estadiaMin = 0;
    }

    /** Copia del depósito con las coordenadas indicadas. */
    public Deposito conCoordenada(Coordenada nueva) {
        return new Deposito(id, nombre, nueva, ventanaDesde, ventanaHasta, estadiaMin);
    }

    /** Ventana horaria del depósito. */
    public VentanaHoraria ventana() {
        return new VentanaHoraria(ventanaDesde, ventanaHasta);
    }
}