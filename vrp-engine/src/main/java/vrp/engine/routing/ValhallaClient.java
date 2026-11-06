package vrp.engine.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vrp.engine.model.Coordenada;

public final class ValhallaClient extends BloquesMatrixProvider implements RouteGeometryProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ValhallaClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String costing;
    private final Duration timeout;

    public ValhallaClient(String baseUrl, String costing, int tamanoBloque, int timeoutSeg) {
        super(tamanoBloque);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeg))
                .build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.costing = costing == null ? "auto" : costing;
        this.timeout = Duration.ofSeconds(timeoutSeg);
    }

    /** Nombre con el que este proveedor aparece en los errores. */
    @Override
    public String nombre() {
        return "valhalla";
    }

    /** Consulta sources_to_targets de Valhalla para un bloque de la matriz. */
    @Override
    protected BloqueMatriz pedirBloque(List<Coordenada> origenes, List<Coordenada> destinos) throws IOException {
        ObjectNode cuerpo = MAPPER.createObjectNode();
        cuerpo.set("sources", puntos(origenes));
        cuerpo.set("targets", puntos(destinos));
        cuerpo.put("costing", costing);

        String jsonBody = MAPPER.writeValueAsString(cuerpo);
        LOG.debug("Valhalla: solicitando matriz {}x{} a {}", origenes.size(), destinos.size(), baseUrl + "/sources_to_targets");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/sources_to_targets"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "VRPEngine/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Solicitud interrumpida", e);
        }

        if (response.statusCode() < 200 || response.statusCode() > 299) {
            String body = response.body() == null ? "" : response.body();
            throw new IOException("HTTP " + response.statusCode() + " en " + baseUrl + ": "
                    + body.substring(0, Math.min(body.length(), 300)));
        }

        JsonNode raiz = MAPPER.readTree(response.body());
        JsonNode filas = raiz.path("sources_to_targets");
        if (!filas.isArray() || filas.size() != origenes.size()) {
            throw new IOException("Respuesta de matriz con formato inesperado");
        }

        double[][] distancias = new double[origenes.size()][destinos.size()];
        long[][] tiempos = new long[origenes.size()][destinos.size()];
        for (int i = 0; i < origenes.size(); i++) {
            JsonNode fila = filas.get(i);
            if (!fila.isArray() || fila.size() != destinos.size()) {
                throw new IOException("Respuesta de matriz con formato inesperado");
            }
            for (int j = 0; j < destinos.size(); j++) {
                JsonNode celda = fila.get(j);
                JsonNode t = celda.path("time");
                JsonNode d = celda.path("distance");
                // Valhalla informa la distancia en km y usa null cuando no hay ruta.
                if (t.isNumber() && d.isNumber()) {
                    tiempos[i][j] = Math.round(t.asDouble());
                    distancias[i][j] = d.asDouble() * 1000.0;
                } else {
                    tiempos[i][j] = MatrizRuteo.NO_RUTA;
                    distancias[i][j] = MatrizRuteo.NO_RUTA;
                }
            }
        }
        LOG.debug("Valhalla: matriz recibida correctamente");
        return new BloqueMatriz(distancias, tiempos);
    }

    /** Obtiene de Valhalla la polilínea de cada tramo entre puntos consecutivos. */
    @Override
    public List<String> tramos(List<Coordenada> puntosEnOrden) {
        if (puntosEnOrden.size() < 2) return List.of();
        try {
            ObjectNode cuerpo = MAPPER.createObjectNode();
            cuerpo.set("locations", puntos(puntosEnOrden));
            cuerpo.put("costing", costing);

            String jsonBody = MAPPER.writeValueAsString(cuerpo);
            LOG.debug("Valhalla: solicitando geometría para {} puntos", puntosEnOrden.size());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/route"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "VRPEngine/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Solicitud interrumpida", e);
            }

            if (response.statusCode() < 200 || response.statusCode() > 299) {
                String body = response.body() == null ? "" : response.body();
                throw new IOException("HTTP " + response.statusCode() + " en " + baseUrl + ": "
                        + body.substring(0, Math.min(body.length(), 300)));
            }

            JsonNode legs = MAPPER.readTree(response.body()).path("trip").path("legs");
            if (!legs.isArray() || legs.size() != puntosEnOrden.size() - 1) {
                throw new IOException("Respuesta de ruta con formato inesperado");
            }
            List<String> salida = new ArrayList<>();
            for (JsonNode leg : legs) {
                salida.add(leg.path("shape").asText(""));
            }
            LOG.debug("Valhalla: geometría recibida correctamente");
            return salida;
        } catch (IOException | RuntimeException e) {
            throw new RoutingException("valhalla: " + e.getMessage(), e);
        }
    }

    /** Convierte coordenadas al formato JSON de Valhalla. */
    private static ArrayNode puntos(List<Coordenada> lista) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (Coordenada c : lista) {
            ObjectNode p = arr.addObject();
            p.put("lat", c.lat());
            p.put("lon", c.lon());
        }
        return arr;
    }
}