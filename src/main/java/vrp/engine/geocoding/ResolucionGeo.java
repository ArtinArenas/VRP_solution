package vrp.engine.geocoding;

import java.util.List;

public record ResolucionGeo(
        String id,
        ResultadoGeo resultado,
        List<String> proveedoresIntentados,
        String proveedorExitoso) {
}
