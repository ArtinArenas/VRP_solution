package vrp.geocoding;

import java.util.Objects;

/**
 * Solicitud de geocodificación para una dirección.
 * Incluye un ID para correlacionar la respuesta con la solicitud original.
 */
public record SolicitudGeocodificacion(
        String id,
        Direccion direccion) {

    public SolicitudGeocodificacion {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(direccion, "direccion no puede ser null");
    }

    public static SolicitudGeocodificacion de(String id, Direccion direccion) {
        return new SolicitudGeocodificacion(id, direccion);
    }
}