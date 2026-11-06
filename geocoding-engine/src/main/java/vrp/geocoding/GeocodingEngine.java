package vrp.geocoding;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fachada principal del motor de geocodificación.
 * <p>
 * Uso típico:
 * <pre>{@code
 * GeocodingConfig cfg = GeocodingConfig.desdeArchivo(Path.of("geocoding.properties"));
 * GeocodingEngine engine = GeocodingEngine.crear(cfg);
 *
 * // Geocodificar una dirección
 * Direccion dir = Direccion.de("Av. Rivadavia 17000", "Haedo", "Buenos Aires");
 * ResultadoGeocodificacion resultado = engine.geocodificar("DIR-1", dir);
 *
 * // Geocodificar múltiples direcciones
 * List<Direccion> direcciones = List.of(dir1, dir2, dir3);
 * Map<String, ResultadoGeocodificacion> resultados = engine.geocodificarDirecciones(direcciones);
 *
 * if (resultado.esExitoso()) {
 *     Coordenada coord = resultado.coordenada();
 *     // usar coordenada...
 * }
 * }</pre>
 */
public final class GeocodingEngine {

    private final GeocodingChain chain;

    private GeocodingEngine(GeocodingChain chain) {
        this.chain = chain;
    }

    /**
     * Crea el motor con configuración completa (archivo + variables de entorno).
     */
    public static GeocodingEngine crear(GeocodingConfig config) {
        return new GeocodingEngine(config.crearChain());
    }

    /**
     * Crea el motor con configuración por defecto (solo Georef público).
     */
    public static GeocodingEngine crearPorDefecto() {
        return new GeocodingEngine(GeocodingConfig.porDefecto().crearChain());
    }

    /**
     * Crea el motor con una cadena personalizada de proveedores (para tests o casos avanzados).
     */
    public static GeocodingEngine crear(GeocodingChain chain) {
        return new GeocodingEngine(chain);
    }

    /**
     * Geocodifica una sola dirección.
     *
     * @param id Identificador único para correlacionar (ej. "CLIENTE-123")
     * @param direccion Dirección a geocodificar
     * @return Resultado con coordenadas o motivo de fallo
     */
    public ResultadoGeocodificacion geocodificar(String id, Direccion direccion) {
        Map<String, ResultadoGeocodificacion> resultados = geocodificarSolicitudes(
                List.of(SolicitudGeocodificacion.de(id, direccion)));
        return resultados.get(id);
    }

    /**
     * Geocodifica múltiples direcciones en una sola llamada.
     * Usa la cadena de proveedores con fallback inteligente y manejo de cuotas.
     *
     * @param direcciones Lista de direcciones a geocodificar
     * @return Mapa id -> ResultadoGeocodificacion (mismo orden que la entrada)
     */
    public Map<String, ResultadoGeocodificacion> geocodificarDirecciones(List<Direccion> direcciones) {
        List<SolicitudGeocodificacion> solicitudes = new ArrayList<>();
        for (int i = 0; i < direcciones.size(); i++) {
            solicitudes.add(SolicitudGeocodificacion.de("ADDR-" + i, direcciones.get(i)));
        }
        return geocodificarSolicitudes(solicitudes);
    }

    /**
     * Geocodifica múltiples solicitudes con IDs personalizados.
     *
     * @param solicitudes Lista de solicitudes con ID y dirección
     * @return Mapa id -> ResultadoGeocodificacion
     */
    public Map<String, ResultadoGeocodificacion> geocodificarSolicitudes(List<SolicitudGeocodificacion> solicitudes) {
        return chain.resolver(solicitudes);
    }

    /**
     * Geocodifica direcciones y retorna solo las exitosas (lanza excepción si hay fallos).
     *
     * @param solicitudes Lista de solicitudes
     * @return Mapa id -> Coordenada (solo exitosas)
     * @throws GeocodingException si alguna dirección no se pudo resolver
     */
    public Map<String, Coordenada> geocodificarExigiendoExito(List<SolicitudGeocodificacion> solicitudes)
            throws GeocodingException {
        Map<String, ResultadoGeocodificacion> resultados = geocodificarSolicitudes(solicitudes);

        Map<String, Coordenada> exitosas = new java.util.LinkedHashMap<>();
        List<String> fallidas = new ArrayList<>();

        for (var entry : resultados.entrySet()) {
            if (entry.getValue().esExitoso()) {
                exitosas.put(entry.getKey(), entry.getValue().coordenada());
            } else {
                fallidas.add(entry.getKey() + ": " + entry.getValue().motivoFallo().descripcion() +
                        " (" + entry.getValue().proveedor() + ")");
            }
        }

        if (!fallidas.isEmpty()) {
            throw new GeocodingException("Fallaron " + fallidas.size() + " de " + solicitudes.size() +
                    " direcciones: " + String.join("; ", fallidas));
        }

        return exitosas;
    }

    /**
     * Obtiene la cadena de proveedores para inspección avanzada.
     */
    public GeocodingChain chain() {
        return chain;
    }

    /**
     * Excepción lanzada cuando geocodificación exigiendo éxito falla.
     */
    public static class GeocodingException extends RuntimeException {
        public GeocodingException(String mensaje) {
            super(mensaje);
        }
    }
}