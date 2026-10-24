package vrp.engine.routing;

import java.util.List;
import vrp.engine.model.Coordenada;

public interface RoutingMatrixProvider {

    /** Nombre del proveedor, para informar errores. */
    String nombre();

    /** Calcula distancias y tiempos entre todos los pares de puntos. */
    MatrizRuteo calcular(List<Coordenada> puntos);
}
