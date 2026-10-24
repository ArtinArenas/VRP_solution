package vrp.engine.service;

import vrp.engine.model.DireccionPostal;
import vrp.engine.model.VentanaHoraria;

public record ModificacionEntrega(
        String entregaId,
        VentanaHoraria ventana,
        boolean quitarVentana,
        Integer prioridad,
        Integer servicioMin,
        DireccionPostal direccion) {

    /** Verdadero si la modificación no cambia nada. */
    public boolean sinCambios() {
        return ventana == null && !quitarVentana && prioridad == null && servicioMin == null && direccion == null;
    }
}
