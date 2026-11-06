package vrp.geocoding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Configuración del motor de geocodificación.
 * Soporta carga desde archivo .properties y variables de entorno.
 */
public record GeocodingConfig(
        String georefUrl,
        String nominatimUrl,
        String nominatimEmail,
        long nominatimPausaMs,
        int timeoutHttpSeg) {

    public static final String GEOREF_URL_DEFECTO = "https://apis.datos.gob.ar/georef/api/v2.0";

    /** Crea una configuración con valores por defecto. */
    public static GeocodingConfig porDefecto() {
        return new GeocodingConfig(
                GEOREF_URL_DEFECTO,
                null,  // Sin Nominatim por defecto
                null,
                0,
                30);
    }

    /** Carga la configuración desde un archivo .properties. */
    public static GeocodingConfig desdeArchivo(Path archivo) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(archivo)) {
            p.load(in);
        }
        return desdeProperties(p);
    }

    /** Arma la configuración con valores del archivo, variables de entorno y por defecto. */
    public static GeocodingConfig desdeProperties(Properties p) {
        return new GeocodingConfig(
                textoConEnv(p, "Georef.url", "GEOREF_URL", GEOREF_URL_DEFECTO),
                textoConEnv(p, "Nominatim.url", "NOMINATIM_URL", null),
                textoConEnv(p, "Nominatim.email", "NOMINATIM_EMAIL", null),
                numeroConEnv(p, "Nominatim.pausaMs", "NOMINATIM_PAUSA_MS", 0),
                (int) numeroConEnv(p, "Http.timeoutSeg", "HTTP_TIMEOUT_SEG", 30));
    }

    /** Lee un texto desde properties, variable de entorno o usa el valor por defecto. */
    private static String textoConEnv(Properties p, String claveProps, String claveEnv, String defecto) {
        String v = p.getProperty(claveProps);
        if (v != null && !v.isBlank()) return v.trim();
        String env = System.getenv(claveEnv);
        if (env != null && !env.isBlank()) return env.trim();
        return defecto;
    }

    /** Lee un número entero desde properties, variable de entorno o usa el valor por defecto. */
    private static long numeroConEnv(Properties p, String claveProps, String claveEnv, long defecto) {
        String v = p.getProperty(claveProps);
        if (v != null && !v.isBlank()) return Long.parseLong(v.trim());
        String env = System.getenv(claveEnv);
        if (env != null && !env.isBlank()) return Long.parseLong(env.trim());
        return defecto;
    }

    /**
     * Crea la cadena de proveedores (GeocodingChain) según la configuración.
     * Orden: Georef (principal, con cuota) -> Nominatim (fallback, sin cuota).
     */
    public GeocodingChain crearChain() {
        List<GeocodingProvider> proveedores = new ArrayList<>();

        // Georef siempre primero (principal, respeta cuota 1000/día)
        if (georefUrl != null && !georefUrl.isBlank()) {
            proveedores.add(new GeorefProvider(georefUrl, timeoutHttpSeg));
        }

        // Nominatim como fallback (opcional, sin cuota dura, rate-limited)
        if (nominatimUrl != null && !nominatimUrl.isBlank()) {
            proveedores.add(new NominatimProvider(nominatimUrl, timeoutHttpSeg, nominatimEmail, nominatimPausaMs));
        }

        if (proveedores.isEmpty()) {
            throw new IllegalStateException("No hay proveedores de geocodificación configurados");
        }

        return new GeocodingChain(proveedores);
    }
}