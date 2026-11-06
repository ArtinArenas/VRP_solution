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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proveedor de geocodificación usando Georef Argentina (datos.gob.ar).
 * Respeta cuota diaria de 1000 consultas y agrupa en lotes (batch).
 */
public final class GeorefProvider implements GeocodingProvider {

    private static final Logger LOG = LoggerFactory.getLogger(GeorefProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int CUOTA_DIARIA = 1000;
    private static final int TAMANO_LOTE = 100; // Georef acepta hasta 100 por request
    private static final String ENDPOINT = "/direcciones.json";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;
    private final AtomicInteger usadasHoy = new AtomicInteger(0);
    private final LocalDate fechaCuota = LocalDate.now();

    public GeorefProvider(String baseUrl, int timeoutSeg) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofSeconds(timeoutSeg);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public String nombre() {
        return "georef";
    }

    @Override
    public boolean tieneCuota() {
        return true;
    }

    @Override
    public int cuotaDisponible() {
        // Resetear contador si cambió el día
        if (!LocalDate.now().equals(fechaCuota)) {
            usadasHoy.set(0);
        }
        return Math.max(0, CUOTA_DIARIA - usadasHoy.get());
    }

    @Override
    public int tamanoMaximoLote() {
        return TAMANO_LOTE;
    }

    @Override
    public Map<String, ResultadoGeocodificacion> geocodificar(List<SolicitudGeocodificacion> solicitudes) {
        Map<String, ResultadoGeocodificacion> resultados = new LinkedHashMap<>();

        // Filtrar solo las que tienen datos válidos para Georef
        List<SolicitudGeocodificacion> validas = solicitudes.stream()
                .filter(s -> s.direccion().validaParaGeoref())
                .toList();

        if (validas.isEmpty()) {
            solicitudes.forEach(s -> resultados.put(s.id(),
                    ResultadoGeocodificacion.Fallido.de(
                            ResultadoGeocodificacion.MotivoFallo.DIRECCION_INVALIDA,
                            "Dirección sin calle o provincia para Georef",
                            nombre())));
            return resultados;
        }

        // Verificar cuota disponible
        int cuota = cuotaDisponible();
        if (cuota <= 0) {
            LOG.warn("Georef: cuota diaria agotada ({}/{})", usadasHoy.get(), CUOTA_DIARIA);
            validas.forEach(s -> resultados.put(s.id(),
                    ResultadoGeocodificacion.Fallido.de(
                            ResultadoGeocodificacion.MotivoFallo.CUOTA_EXCEDIDA,
                            "Cuota diaria de Georef agotada",
                            nombre())));
            // Las que no son válidas para Georef también marcan como fallo
            solicitudes.stream()
                    .filter(s -> !validas.contains(s))
                    .forEach(s -> resultados.put(s.id(),
                            ResultadoGeocodificacion.Fallido.de(
                                    ResultadoGeocodificacion.MotivoFallo.DIRECCION_INVALIDA,
                                    "Dirección sin calle o provincia para Georef",
                                    nombre())));
            return resultados;
        }

        // Procesar en lotes, respetando la cuota restante
        int aProcesar = Math.min(validas.size(), cuota);
        List<SolicitudGeocodificacion> paraProcesar = validas.subList(0, aProcesar);
        List<SolicitudGeocodificacion> excedenCuota = validas.size() > aProcesar
                ? validas.subList(aProcesar, validas.size())
                : List.of();

        // Procesar lotes
        for (int i = 0; i < paraProcesar.size(); i += TAMANO_LOTE) {
            int fin = Math.min(i + TAMANO_LOTE, paraProcesar.size());
            List<SolicitudGeocodificacion> lote = paraProcesar.subList(i, fin);
            Map<String, ResultadoGeocodificacion> loteResultados = procesarLote(lote);
            resultados.putAll(loteResultados);
        }

        // Las que exceden la cuota van como fallo por cuota
        excedenCuota.forEach(s -> resultados.put(s.id(),
                ResultadoGeocodificacion.Fallido.de(
                        ResultadoGeocodificacion.MotivoFallo.CUOTA_EXCEDIDA,
                        "Excede cuota diaria de Georef",
                        nombre())));

        // Las que no son válidas para Georef
        solicitudes.stream()
                .filter(s -> !validas.contains(s))
                .forEach(s -> resultados.put(s.id(),
                        ResultadoGeocodificacion.Fallido.de(
                                ResultadoGeocodificacion.MotivoFallo.DIRECCION_INVALIDA,
                                "Dirección sin calle o provincia para Georef",
                                nombre())));

        return resultados;
    }

    private Map<String, ResultadoGeocodificacion> procesarLote(List<SolicitudGeocodificacion> lote) {
        Map<String, ResultadoGeocodificacion> resultados = new LinkedHashMap<>();

        try {
            // Construir request JSON para Georef
            var direccionesJson = MAPPER.createArrayNode();
            for (SolicitudGeocodificacion s : lote) {
                var obj = MAPPER.createObjectNode();
                obj.put("direccion", s.direccion().calle());
                if (!s.direccion().localidad().isBlank()) obj.put("localidad", s.direccion().localidad());
                if (!s.direccion().provincia().isBlank()) obj.put("provincia", s.direccion().provincia());
                direccionesJson.add(obj);
            }

            var requestBody = MAPPER.createObjectNode();
            requestBody.set("direcciones", direccionesJson);

            String jsonBody = MAPPER.writeValueAsString(requestBody);
            LOG.debug("Georef: geocodificando lote de {} direcciones", lote.size());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + ENDPOINT))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "VRP-Geocoding/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lote.forEach(s -> resultados.put(s.id(),
                        ResultadoGeocodificacion.Fallido.de(
                                ResultadoGeocodificacion.MotivoFallo.ERROR_COMUNICACION,
                                "Solicitud interrumpida",
                                nombre())));
                return resultados;
            }

            if (response.statusCode() < 200 || response.statusCode() > 299) {
                LOG.warn("Georef: HTTP {} - {}", response.statusCode(), response.body());
                lote.forEach(s -> resultados.put(s.id(),
                        ResultadoGeocodificacion.Fallido.de(
                                ResultadoGeocodificacion.MotivoFallo.PROVEEDOR_NO_DISPONIBLE,
                                "HTTP " + response.statusCode(),
                                nombre())));
                return resultados;
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode resultadosNode = root.path("resultados");

            if (!resultadosNode.isArray() || resultadosNode.size() != lote.size()) {
                LOG.warn("Georef: respuesta con formato inesperado");
                lote.forEach(s -> resultados.put(s.id(),
                        ResultadoGeocodificacion.Fallido.de(
                                ResultadoGeocodificacion.MotivoFallo.ERROR_INTERNO,
                                "Respuesta con formato inesperado",
                                nombre())));
                return resultados;
            }

            // Incrementar contador de cuota
            usadasHoy.addAndGet(lote.size());

            for (int i = 0; i < lote.size(); i++) {
                SolicitudGeocodificacion solicitud = lote.get(i);
                JsonNode resultado = resultadosNode.get(i);

                if (resultado.path("cantidad").asInt(0) > 0) {
                    JsonNode primera = resultado.path("direcciones").get(0);
                    JsonNode coordenadas = primera.path("coordenadas");
                    double lat = coordenadas.path("lat").asDouble(0.0);
                    double lon = coordenadas.path("lon").asDouble(0.0);

                    if (lat != 0.0 || lon != 0.0) {
                        Coordenada coord = new Coordenada(lat, lon);
                        // Verificar que esté en Argentina
                        if (coord.enArgentina()) {
                            resultados.put(solicitud.id(),
                                    ResultadoGeocodificacion.Exitoso.de(coord, nombre(), 0.95));
                        } else {
                            resultados.put(solicitud.id(),
                                    ResultadoGeocodificacion.Fallido.de(
                                            ResultadoGeocodificacion.MotivoFallo.FUERA_DE_AREA,
                                            "Coordenadas fuera de Argentina: " + coord,
                                            nombre()));
                        }
                    } else {
                        resultados.put(solicitud.id(),
                                ResultadoGeocodificacion.Fallido.de(
                                        ResultadoGeocodificacion.MotivoFallo.NO_ENCONTRADA,
                                        "Georef no encontró coordenadas válidas",
                                        nombre()));
                    }
                } else {
                    resultados.put(solicitud.id(),
                            ResultadoGeocodificacion.Fallido.de(
                                    ResultadoGeocodificacion.MotivoFallo.NO_ENCONTRADA,
                                    "Georef no encontró la dirección",
                                    nombre()));
                }
            }

            LOG.debug("Georef: lote procesado exitosamente ({} direcciones)", lote.size());

        } catch (IOException e) {
            LOG.error("Georef: error procesando lote", e);
            lote.forEach(s -> resultados.put(s.id(),
                    ResultadoGeocodificacion.Fallido.de(
                            ResultadoGeocodificacion.MotivoFallo.ERROR_COMUNICACION,
                            e.getMessage(),
                            nombre())));
        }

        return resultados;
    }
}