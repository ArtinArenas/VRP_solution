package vrp.engine.model;

public record Entrega(
        String id,
        String nombre,
        DireccionPostal direccion,
        Coordenada coordenada,
        int demanda,
        int servicioMin,
        VentanaHoraria ventana,
        int prioridad) {

    /** Normaliza la prioridad: cualquier valor menor a 1 pasa a 1. */
    public Entrega {
        if (prioridad <= 0) prioridad = 1;
    }

    /** Copia de la entrega con las coordenadas indicadas. */
    public Entrega conCoordenada(Coordenada nueva) {
        return new Entrega(id, nombre, direccion, nueva, demanda, servicioMin, ventana, prioridad);
    }
}
