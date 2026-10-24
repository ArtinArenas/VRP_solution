package vrp.engine.routing;

import java.util.List;
import vrp.engine.model.Coordenada;

public interface RouteGeometryProvider {

    /** Nombre del proveedor, para informar errores. */
    String nombre();

    /** Devuelve una polilínea por cada par de puntos consecutivos. */
    List<String> tramos(List<Coordenada> puntosEnOrden);
}
