package vrp.geocoding;

import java.util.List;
import java.util.Map;

/**
 * Interfaz para proveedores de geocodificación.
 * Cada proveedor recibe una lista de solicitudes y devuelve un mapa id -> resultado.
 */
public interface GeocodingProvider {

    /**
     * Nombre único del proveedor (para logging y debugging).
     */
    String nombre();

    /**
     * Geocodifica una lista de direcciones.
     * @param solicitudes Lista de solicitudes con id y dirección a geocodificar
     * @return Mapa id -> ResultadoGeocodificacion (éxito con coordenadas o fallo con motivo)
     */
    Map<String, ResultadoGeocodificacion> geocodificar(List<SolicitudGeocodificacion> solicitudes);

    /**
     * Indica si el proveedor tiene límite de cuota (ej. Georef 1000/día).
     * Si retorna true, se debe consultar cuotaDisponible().
     */
    default boolean tieneCuota() {
        return false;
    }

    /**
     * Cuota disponible en este momento (si tieneCuota() == true).
     * @return Número de solicitudes que aún puede procesar, o -1 si ilimitado
     */
    default int cuotaDisponible() {
        return -1;
    }

    /**
     * Indica si el proveedor soporta procesamiento por lotes (batch).
     * Si false, el chain enviará de a una.
     */
    default boolean soportaLotes() {
        return true;
    }

    /**
     * Tamaño máximo de lote que soporta el proveedor.
     */
    default int tamanoMaximoLote() {
        return 100;
    }
}