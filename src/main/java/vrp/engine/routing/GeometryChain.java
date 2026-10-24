package vrp.engine.routing;

import java.util.ArrayList;
import java.util.List;
import vrp.engine.model.Coordenada;

public final class GeometryChain implements RouteGeometryProvider {

    private final List<RouteGeometryProvider> proveedores;

    public GeometryChain(List<RouteGeometryProvider> proveedores) {
        this.proveedores = List.copyOf(proveedores);
    }

    /** Nombre de la cadena de proveedores. */
    @Override
    public String nombre() {
        return "cadena";
    }

    /** Devuelve las polilíneas del primer proveedor que responda bien. */
    @Override
    public List<String> tramos(List<Coordenada> puntosEnOrden) {
        List<String> errores = new ArrayList<>();
        for (RouteGeometryProvider p : proveedores) {
            try {
                List<String> t = p.tramos(puntosEnOrden);
                if (t.size() == Math.max(0, puntosEnOrden.size() - 1)) return t;
                errores.add(p.nombre() + ": cantidad de tramos inesperada");
            } catch (RoutingException e) {
                errores.add(e.getMessage());
            }
        }
        throw new RoutingException("Geometria no disponible: " + String.join(" | ", errores));
    }
}
