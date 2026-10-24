package vrp.engine.routing;

import java.util.ArrayList;
import java.util.List;
import vrp.engine.model.Coordenada;

public final class MatrixChain implements RoutingMatrixProvider {

    private final List<RoutingMatrixProvider> proveedores;

    public MatrixChain(List<RoutingMatrixProvider> proveedores) {
        this.proveedores = List.copyOf(proveedores);
    }

    /** Nombre de la cadena de proveedores. */
    @Override
    public String nombre() {
        return "cadena";
    }

    /** Prueba los proveedores en orden y completa con los siguientes los pares sin ruta. */
    @Override
    public MatrizRuteo calcular(List<Coordenada> puntos) {
        MatrizRuteo acumulada = null;
        List<String> errores = new ArrayList<>();
        for (RoutingMatrixProvider p : proveedores) {
            try {
                MatrizRuteo m = p.calcular(puntos);
                // El proveedor siguiente solo completa los pares que el anterior no pudo calcular.
                acumulada = acumulada == null ? m : acumulada.completarCon(m);
                if (acumulada.sinHuecos()) return acumulada;
            } catch (RoutingException e) {
                errores.add(e.getMessage());
            }
        }
        if (acumulada != null) return acumulada;
        throw new RoutingException("Ningun proveedor de ruteo disponible: " + String.join(" | ", errores));
    }
}
