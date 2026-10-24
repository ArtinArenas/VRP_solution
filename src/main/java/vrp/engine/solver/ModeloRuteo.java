package vrp.engine.solver;

import java.util.List;
import vrp.engine.model.Coordenada;
import vrp.engine.model.Entrega;
import vrp.engine.model.Vehiculo;

public record ModeloRuteo(
        List<String> idsNodo,
        List<Coordenada> coordenadas,
        int[] inicios,
        int[] fines,
        List<Vehiculo> vehiculos,
        List<Entrega> entregas,
        int primerNodoEntrega,
        long[] salidaSeg,
        long[] limiteSeg,
        long[] capacidad,
        long[] demanda,
        long[] servicioSeg,
        long[] desdeSeg,
        long[] hastaSeg,
        long[] penalizacion) {

    public static final long HORIZONTE_SEG = 172_800L;

    /** Cantidad total de nodos del modelo. */
    public int nodos() {
        return idsNodo.size();
    }

    /** Verdadero si el nodo es un cliente y no un depósito o posición. */
    public boolean esEntrega(int nodo) {
        return nodo >= primerNodoEntrega;
    }

    /** Entrega correspondiente a un nodo cliente. */
    public Entrega entregaDeNodo(int nodo) {
        return entregas.get(nodo - primerNodoEntrega);
    }
}
