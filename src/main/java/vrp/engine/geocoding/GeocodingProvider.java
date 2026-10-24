package vrp.engine.geocoding;

import java.util.List;
import java.util.Map;

public interface GeocodingProvider {

    /** Nombre del proveedor, para informar qué servicios se intentaron. */
    String nombre();

    /** Resuelve las direcciones y devuelve un resultado por cada id solicitado. */
    Map<String, ResultadoGeo> geocodificar(List<SolicitudGeocodificacion> solicitudes);
}
