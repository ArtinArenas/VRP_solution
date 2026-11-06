package vrp.engine.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import vrp.engine.util.Polyline6;

public final class OsrmClient extends BloquesMatrixProvider implements RouteGeometryProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OsrmClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;

    public OsrmClient(String baseUrl, int tamanoBloque, int timeoutSeg) {
        super(tamanoBloque);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeg))
                .build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofSeconds(timeoutSeg);
    }

    /** Nombre con el que este proveedor aparece en los errores. */
    @Override
    public String nombre() {
        return "osrm";
    }

    /** Consulta la tabla de OSRM para un bloque de la matriz. */
    @Override
    protected BloqueMatriz pedirBloque(List<Coordenada> origenes, List<Coordenada> destinos) throws IOException {
        // Si origen y destino son iguales se usa la tabla completa, sin sources ni destinations.
        boolean cuadrada = origenes.equals(destinos);
        List<Coordenada> todas = new ArrayList<>(origenes);
        if (!cuadrada) todas.addAll(destinos);

        StringBuilder url = new StringBuilder(baseUrl)
                .append("/table/v1/driving/").append(lista(todas))
                .append("?annotations=distance,duration");
        if (!cuadrada) {
            url.append("&sources=").append(indices(0, origenes.size()))
                    .append("&destinations=").append(indices(origenes.size(), destinos.size()));
        }

        LOG.debug("OSRM: solicitando matriz {}x{} a {}", origenes.size(), destinos.size(), url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "VRPEngine/1.0")
                .GET()
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
        if (!"Ok".equals(raiz.path("code").asText())) {
            throw new IOException("OSRM no pudo calcular la matriz: " + raiz.path("message").asText(raiz.path("code").asText()));
        }
        JsonNode dist = raiz.path("distances");
        JsonNode dur = raiz.path("durations");
        if (!dist.isArray() || !dur.isArray() || dist.size() != origenes.size() || dur.size() != origenes.size()) {
            throw new IOException("Respuesta de matriz con formato inesperado");
        }

        double[][] distancias = new double[origenes.size()][destinos.size()];
        long[][] tiempos = new long[origenes.size()][destinos.size()];
        for (int i = 0; i < origenes.size(); i++) {
            if (dist.get(i).size() != destinos.size() || dur.get(i).size() != destinos.size()) {
                throw new IOException("Respuesta de matriz con formato inesperado");
            }
            for (int j = 0; j < destinos.size(); j++) {
                JsonNode d = dist.get(i).get(j);
                JsonNode t = dur.get(i).get(j);
                if (d.isNumber() && t.isNumber()) {
                    distancias[i][j] = d.asDouble();
                    tiempos[i][j] = Math.round(t.asDouble());
                } else {
                    distancias[i][j] = MatrizRuteo.NO_RUTA;
                    tiempos[i][j] = MatrizRuteo.NO_RUTA;
                }
            }
        }
        LOG.debug("OSRM: matriz recibida correctamente");
        return new BloqueMatriz(distancias, tiempos);
    }

    /** Obtiene de OSRM la polilínea de cada tramo entre puntos consecutivos. */
    @Override
    public List<String> tramos(List<Coordenada> puntosEnOrden) {
        if (puntosEnOrden.size() < 2) return List.of();
        try {
            String url = baseUrl + "/route/v1/driving/" + lista(puntosEnOrden)
                    + "?overview=false&steps=true&geometries=polyline6";
            LOG.debug("OSRM: solicitando geometría para {} puntos", puntosEnOrden.size());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("User-Agent", "VRPEngine/1.0")
                    .GET()
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
            JsonNode legs = raiz.path("routes").path(0).path("legs");
            if (!"Ok".equals(raiz.path("code").asText()) || !legs.isArray()
                    || legs.size() != puntosEnOrden.size() - 1) {
                throw new IOException("Respuesta de ruta con formato inesperado");
            }
            List<String> salida = new ArrayList<>();
            for (JsonNode leg : legs) {
                List<Coordenada> puntos = new ArrayList<>();
                // OSRM da la geometría por paso; se unen los pasos de cada tramo.
                for (JsonNode step : leg.path("steps")) {
                    for (Coordenada c : Polyline6.decodificar(step.path("geometry").asText(""))) {
                        if (puntos.isEmpty() || !puntos.get(puntos.size() - 1).equals(c)) puntos.add(c);
                    }
                }
                salida.add(Polyline6.codificar(puntos));
            }
            LOG.debug("OSRM: geometría recibida correctamente");
            return salida;
        } catch (IOException | RuntimeException e) {
            throw new RoutingException("osrm: " + e.getMessage(), e);
        }
    }

    /** Formatea coordenadas como "lon,lat;lon,lat" para la URL de OSRM. */
    private static String lista(List<Coordenada> coords) {
        StringBuilder sb = new StringBuilder();
        for (Coordenada c : coords) {
            if (sb.length() > 0) sb.append(';');
            sb.append(c.lon()).append(',').append(c.lat());
        }
        return sb.toString();
    }

    /** Arma la lista de índices "a;b;c" para sources y destinations. */
    private static String indices(int desde, int cantidad) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cantidad; i++) {
            if (i > 0) sb.append(';');
            sb.append(desde + i);
        }
        return sb.toString();
    }
}