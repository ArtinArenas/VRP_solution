package vrp.engine.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vrp.engine.http.HttpTransporte;
import vrp.engine.model.Coordenada;
import vrp.engine.model.DireccionPostal;

public final class GeorefProvider implements GeocodingProvider {

    private static final int TAMANO_LOTE = 1000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpTransporte http;
    private final String baseUrl;

    public GeorefProvider(HttpTransporte http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Nombre con el que este proveedor aparece en los errores. */
    @Override
    public String nombre() {
        return "georef";
    }

    /** Geocodifica con Georef en lotes de hasta 1000 direcciones. */
    @Override
    public Map<String, ResultadoGeo> geocodificar(List<SolicitudGeocodificacion> solicitudes) {
        Map<String, ResultadoGeo> salida = new LinkedHashMap<>();
        // Georef admite hasta 1000 consultas por petición.
        for (int i = 0; i < solicitudes.size(); i += TAMANO_LOTE) {
            List<SolicitudGeocodificacion> lote =
                    solicitudes.subList(i, Math.min(i + TAMANO_LOTE, solicitudes.size()));
            try {
                procesarLote(lote, salida);
            } catch (IOException | RuntimeException e) {
                // Si falla el lote completo, todas pasan al siguiente proveedor.
                for (SolicitudGeocodificacion s : lote) {
                    salida.put(s.id(), ResultadoGeo.fallo(MotivoGeo.PROVEEDOR_NO_DISPONIBLE,
                            "Georef no disponible: " + e.getMessage()));
                }
            }
        }
        return salida;
    }

    /** Envía un lote a Georef y traduce cada resultado. */
    private void procesarLote(List<SolicitudGeocodificacion> lote, Map<String, ResultadoGeo> salida)
            throws IOException {
        ObjectNode raiz = MAPPER.createObjectNode();
        ArrayNode consultas = raiz.putArray("direcciones");
        for (SolicitudGeocodificacion s : lote) {
            DireccionPostal d = s.direccion();
            ObjectNode q = consultas.addObject();
            q.put("direccion", d.direccion());
            if (noVacio(d.provincia())) q.put("provincia", d.provincia().trim());
            if (noVacio(d.localidad())) q.put("localidad", d.localidad().trim());
            q.put("max", 1);
        }

        String respuesta = http.post(URI.create(baseUrl + "/direcciones"), MAPPER.writeValueAsString(raiz));
        JsonNode resultados = MAPPER.readTree(respuesta).path("resultados");
        // Georef responde un resultado por consulta, en el mismo orden.
        if (!resultados.isArray() || resultados.size() != lote.size()) {
            throw new IOException("Respuesta de Georef con formato inesperado");
        }

        for (int i = 0; i < lote.size(); i++) {
            String id = lote.get(i).id();
            JsonNode direcciones = resultados.get(i).path("direcciones");
            if (!direcciones.isArray() || direcciones.size() == 0) {
                salida.put(id, ResultadoGeo.fallo(MotivoGeo.NO_ENCONTRADA, "Georef no encontro la direccion"));
                continue;
            }
            JsonNode d = direcciones.get(0);
            JsonNode ubicacion = d.path("ubicacion");
            if (ubicacion.path("lat").isNumber() && ubicacion.path("lon").isNumber()) {
                String normalizada = d.path("nomenclatura").isTextual() ? d.path("nomenclatura").asText() : null;
                salida.put(id, ResultadoGeo.ok(
                        new Coordenada(ubicacion.path("lat").asDouble(), ubicacion.path("lon").asDouble()),
                        normalizada));
            } else {
                salida.put(id, ResultadoGeo.fallo(MotivoGeo.SIN_COORDENADAS,
                        "Georef normalizo la direccion pero no pudo ubicarla"));
            }
        }
    }

    /** Verdadero si el texto tiene contenido. */
    private static boolean noVacio(String s) {
        return s != null && !s.isBlank();
    }
}
