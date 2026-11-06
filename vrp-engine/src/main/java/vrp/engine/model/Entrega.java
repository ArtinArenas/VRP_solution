package vrp.engine.model;

public record Entrega(
        String id,
        String nombre,
        Coordenada coordenada,
        int demanda,
        int servicioMin,
        VentanaHoraria ventana,
        int prioridad) {

    public Entrega {
        if (coordenada == null) throw new IllegalArgumentException("Coordenada es obligatoria para Entrega");
        if (prioridad <= 0) prioridad = 1;
    }

    /** Copia de la entrega con las coordenadas indicadas. */
    public Entrega conCoordenada(Coordenada nueva) {
        return new Entrega(id, nombre, nueva, demanda, servicioMin, ventana, prioridad);
    }
}