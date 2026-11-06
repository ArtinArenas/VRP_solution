package vrp.geocoding;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dirección postal estructurada para geocodificación.
 * Los campos se usan según lo que requiera cada proveedor (Georef usa provincia/localidad, Nominatim usa texto libre).
 */
public record Direccion(
        String calle,           // Ej: "Av. Rivadavia 17000"
        String localidad,       // Ej: "Haedo" (opcional)
        String provincia,       // Ej: "Buenos Aires" (opcional, obligatorio para Georef)
        String codigoPostal,    // Ej: "1706" (opcional)
        String pais) {          // Ej: "Argentina" (opcional, default)

    public Direccion {
        calle = calle == null ? "" : calle.trim();
        localidad = localidad == null ? "" : localidad.trim();
        provincia = provincia == null ? "" : provincia.trim();
        codigoPostal = codigoPostal == null ? "" : codigoPostal.trim();
        pais = pais == null || pais.isBlank() ? "Argentina" : pais.trim();
    }

    /**
     * Texto completo de la dirección para proveedores que aceptan texto libre (Nominatim, Google).
     */
    public String textoCompleto() {
        return Stream.of(calle, localidad, provincia, codigoPostal, pais)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    /**
     * Texto para Georef (calle + localidad + provincia).
     */
    public String textoGeoref() {
        return Stream.of(calle, localidad, provincia)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    /**
     * Verifica si tiene los campos mínimos para Georef (calle y provincia).
     */
    public boolean validaParaGeoref() {
        return !calle.isBlank() && !provincia.isBlank();
    }

    /**
     * Verifica si tiene datos útiles para geocodificar.
     */
    public boolean tieneDatos() {
        return !calle.isBlank() || !localidad.isBlank() || !provincia.isBlank();
    }

    public static Direccion de(String calle, String localidad, String provincia) {
        return new Direccion(calle, localidad, provincia, "", "Argentina");
    }

    public static Direccion deTextoLibre(String texto) {
        return new Direccion(texto, "", "", "", "Argentina");
    }
}