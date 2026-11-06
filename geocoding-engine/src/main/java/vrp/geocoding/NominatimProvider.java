package vrp.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proveedor de geocodificación usando Nominatim local (OpenStreetMap).
 * Soporta rate limiting configurable (pausa entre requests).
 * No tiene cuota dura, pero respeta límites de uso razonable.
 */
public final class NominatimProvider implements GeocodingProvider {

    private static final Logger LOG = LoggerFactory.getLogger(NominatimProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;
    private final String emailContacto;
    private final long pausaEntreRequestsMs;
    private final AtomicLong ultimaPeticion = new AtomicLong(0);

    public NominatimProvider(String baseUrl, int timeoutSeg, String emailContacto, long pausaMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofSeconds(timeoutSeg);
        this.emailContacto = emailContacto == null ? "" : emailContacto.trim();
        this.pausaEntreRequestsMs = Math.max(0, pausaMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public String nombre() {
        return "nominatim";
    }

    @Override
    public boolean tieneCuota() {
        return false; // Nominatim local no tiene cuota dura
    }

    @Override
    public boolean soportaLotes() {
        return false; // Nominatim no soporta batch nativo, se hace uno por uno
    }

    @Override
    public Map<String, ResultadoGeocodificacion> geocodificar(List<SolicitudGeocodificacion> solicitudes) {
        Map<String, ResultadoGeocodificacion> resultados = new LinkedHashMap<>();

        for (SolicitudGeocodificacion s : solicitudes) {
            // Rate limiting
            if (pausaEntreRequestsMs > 0) {
                long ahora = System.currentTimeMillis();
                long ultima = ultimaPeticion.get();
                long espera = ultima + pausaEntreRequestsMs - ahora;
                if (espera > 0) {
                    try {
                        Thread.sleep(espera);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        resultados.put(s.id(),
                                ResultadoGeocodificacion.Fallido.de(
                                        ResultadoGeocodificacion.MotivoFallo.ERROR_COMUNICACION,
                                        "Interrumpido en rate limiting",
                                        nombre()));
                        continue;
                    }
                }
                ultimaPeticion.set(System.currentTimeMillis());
            }

            ResultadoGeocodificacion resultado = geocodificarUno(s);
            resultados.put(s.id(), resultado);
        }

        return resultados;
    }

    private ResultadoGeocodificacion geocodificarUno(SolicitudGeocodificacion solicitud) {
        Direccion dir = solicitud.direccion();

        // Nominatim usa búsqueda de texto libre
        String query = dir.textoCompleto();
        if (query.isBlank()) {
            return ResultadoGeocodificacion.Fallido.de(
                    ResultadoGeocodificacion.MotivoFallo.DIRECCION_INVALIDA,
                    "Dirección vacía",
                    nombre());
        }

        try {
            String url = baseUrl + "/search" +
                    "?q=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&format=json" +
                    "&limit=1" +
                    "&addressdetails=1";

            LOG.debug("Nominatim: geocodificando '{}'", query);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("User-Agent", "VRP-Geocoding/1.0" + (emailContacto.isEmpty() ? "" : " (contact: " + emailContacto + ")"))
                    .GET()
                    .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResultadoGeocodificacion.Fallido.de(
                        ResultadoGeocodificacion.MotivoFallo.ERROR_COMUNICACION,
                        "Solicitud interrumpida",
                        nombre());
            }

            if (response.statusCode() < 200 || response.statusCode() > 299) {
                LOG.warn("Nominatim: HTTP {} - {}", response.statusCode(), response.body());
                return ResultadoGeocodificacion.Fallido.de(
                        ResultadoGeocodificacion.MotivoFallo.PROVEEDOR_NO_DISPONIBLE,
                        "HTTP " + response.statusCode(),
                        nombre());
            }

            JsonNode resultados = MAPPER.readTree(response.body());
            if (!resultados.isArray() || resultados.isEmpty()) {
                return ResultadoGeocodificacion.Fallido.de(
                        ResultadoGeocodificacion.MotivoFallo.NO_ENCONTRADA,
                        "Nominatim no encontró resultados",
                        nombre());
            }

            JsonNode primero = resultados.get(0);
            double lat = primero.path("lat").asDouble(0.0);
            double lon = primero.path("lon").asDouble(0.0);

            if (lat == 0.0 && lon == 0.0) {
                return ResultadoGeocodificacion.Fallido.de(
                        ResultadoGeocodificacion.MotivoFallo.SIN_COORDENADAS_VALIDAS,
                        "Coordenadas (0,0) inválidas",
                        nombre());
            }

            Coordenada coord = new Coordenada(lat, lon);

            // Verificar que esté en Argentina (aproximado)
            double confianza = coord.enArgentina() ? 0.9 : 0.7;

            LOG.debug("Nominatim: encontrado {} ({})", coord, primero.path("display_name").asText(""));
            return ResultadoGeocodificacion.Exitoso.de(coord, nombre(), confianza);

        } catch (IOException e) {
            LOG.error("Nominatim: error geocodificando '{}'", query, e);
            return ResultadoGeocodificacion.Fallido.de(
                    ResultadoGeocodificacion.MotivoFallo.ERROR_COMUNICACION,
                    e.getMessage(),
                    nombre());
        }
    }
}