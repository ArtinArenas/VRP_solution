package vrp.engine.solver;

import java.util.ArrayList;
import java.util.List;

public record SolucionVrp(List<RutaNodos> rutas, List<Integer> omitidos, boolean sinSolucion) {

    /** Solución en la que todas las entregas quedan omitidas. */
    public static SolucionVrp sinSolucion(ModeloRuteo modelo) {
        List<RutaNodos> rutas = new ArrayList<>();
        for (int v = 0; v < modelo.vehiculos().size(); v++) rutas.add(new RutaNodos(v, List.of()));
        List<Integer> omitidos = new ArrayList<>();
        for (int i = modelo.primerNodoEntrega(); i < modelo.nodos(); i++) omitidos.add(i);
        return new SolucionVrp(rutas, omitidos, true);
    }
}
