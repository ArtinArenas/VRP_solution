package vrp.engine.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vrp.engine.http.HttpTransporte;
import vrp.engine.model.Coordenada;

public final class NominatimLocalProvider implements GeocodingProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpTransporte http;
    private final String baseUrl;
    private final String email;
    private final long pausaMs;

    public NominatimLocalProvider(HttpTransporte http, String baseUrl, String email, long pausaMs) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.email = email;
        this.pausaMs = pausaMs;
    }

    /** Nombre con el que este proveedor aparece en los errores. */
    @Override
    public String nombre() {
        return "nominatim";
    }

    /** Geocodifica con Nominatim, una consulta por dirección. */
    @Override
    public Map<String, ResultadoGeo> geocodificar(List<SolicitudGeocodificacion> solicitudes) {
        Map<String, ResultadoGeo> salida = new LinkedHashMap<>();
        boolean primera = true;
        for (SolicitudGeocodificacion s : solicitudes) {
            // Pausa opcional entre consultas (solo hace falta con instancias públicas).
            if (!primera && pausaMs > 0) {
                try {
                    Thread.sleep(pausaMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    salida.put(s.id(), ResultadoGeo.fallo(MotivoGeo.PROVEEDOR_NO_DISPONIBLE, "Interrumpido"));
                    continue;
                }
            }
            primera = false;
            salida.put(s.id(), consultar(s));
        }
        return salida;
    }

    /** Consulta una dirección y toma el primer resultado. */
    private ResultadoGeo consultar(SolicitudGeocodificacion s) {
        String q = URLEncoder.encode(s.direccion().texto() + ", Argentina", StandardCharsets.UTF_8);
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/search?format=jsonv2&limit=1&countrycodes=ar&q=").append(q);
        if (email != null && !email.isBlank()) {
            url.append("&email=").append(URLEncoder.encode(email, StandardCharsets.UTF_8));
        }
        try {
            JsonNode resultados = MAPPER.readTree(http.get(URI.create(url.toString())));
            if (!resultados.isArray() || resultados.size() == 0) {
                return ResultadoGeo.fallo(MotivoGeo.NO_ENCONTRADA, "Nominatim no encontro resultados");
            }
            JsonNode r = resultados.get(0);
            double lat = Double.parseDouble(r.path("lat").asText());
            double lon = Double.parseDouble(r.path("lon").asText());
            String nombre = r.path("display_name").isTextual() ? r.path("display_name").asText() : null;
            return ResultadoGeo.ok(new Coordenada(lat, lon), nombre);
        } catch (IOException | RuntimeException e) {
            return ResultadoGeo.fallo(MotivoGeo.PROVEEDOR_NO_DISPONIBLE, "Nominatim no disponible: " + e.getMessage());
        }
    }
}
