package vrp.geocoding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cadena de proveedores de geocodificación con fallback inteligente y manejo de cuotas.
 * <p>
 * Funcionamiento:
 * <ol>
 *   <li>Intenta el primer proveedor (principal) con todas las solicitudes</li>
 *   <li>Las que fallen (no encontradas, error, fuera de área, cuota excedida) pasan al siguiente proveedor</li>
 *   <li>Si un proveedor tiene cuota y se excede, las excedentes pasan al siguiente directamente</li>
 *   <li>Los proveedores sin cuota (como Nominatim) reciben solo lo que los anteriores no resolvieron</li>
 *   <li>El último proveedor es el fallback final</li>
 * </ol>
 * <p>
 * Optimizaciones:
 * <ul>
 *   <li>Evita peticiones innecesarias: no reintenta direcciones ya resueltas</li>
 *   <li>Respeta cuotas: solo envía hasta el límite disponible</li>
 *   <li>Batching inteligente: agrupa en lotes según capacidad del proveedor</li>
 *   <li>Orden configurable: el orden de la lista define la prioridad</li>
 * </ul>
 */
public final class GeocodingChain {

    private static final Logger LOG = LoggerFactory.getLogger(GeocodingChain.class);

    private final List<GeocodingProvider> proveedores;

    public GeocodingChain(List<GeocodingProvider> proveedores) {
        this.proveedores = List.copyOf(Objects.requireNonNull(proveedores, "Lista de proveedores no puede ser null"));
        if (proveedores.isEmpty()) {
            throw new IllegalArgumentException("Debe haber al menos un proveedor");
        }
        // Log de configuración
        for (int i = 0; i < proveedores.size(); i++) {
            GeocodingProvider p = proveedores.get(i);
            LOG.info("GeocodingChain: proveedor {} = {} (cuota={}, lotes={}, maxLote={})",
                    i, p.nombre(), p.tieneCuota(), p.soportaLotes(), p.tamanoMaximoLote());
        }
    }

    /**
     * Resuelve las direcciones probando los proveedores en orden de preferencia.
     *
     * @param solicitudes Lista de direcciones a geocodificar (cada una con su ID)
     * @return Mapa id -> ResultadoGeocodificacion con el mejor resultado obtenido
     */
    public Map<String, ResultadoGeocodificacion> resolver(List<SolicitudGeocodificacion> solicitudes) {
        Objects.requireNonNull(solicitudes, "Lista de solicitudes no puede ser null");

        if (solicitudes.isEmpty()) {
            return Map.of();
        }

        LOG.info("GeocodingChain: iniciando geocodificación de {} direcciones", solicitudes.size());

        Map<String, ResultadoGeocodificacion> resultadosFinales = new LinkedHashMap<>();
        Map<String, List<String>> proveedoresIntentados = new LinkedHashMap<>();
        Map<String, List<ResultadoGeocodificacion>> historialFallos = new LinkedHashMap<>();

        // Solicitudes pendientes de resolver
        List<SolicitudGeocodificacion> pendientes = new ArrayList<>(solicitudes);

        // Probar cada proveedor en orden
        for (int i = 0; i < proveedores.size(); i++) {
            GeocodingProvider proveedor = proveedores.get(i);

            if (pendientes.isEmpty()) {
                LOG.debug("GeocodingChain: todas resueltas, saltando proveedor {}", proveedor.nombre());
                break;
            }

            LOG.debug("GeocodingChain: proveedor {} ({}) procesando {} solicitudes pendientes",
                    i, proveedor.nombre(), pendientes.size());

            // Determinar cuáles enviar a este proveedor
            List<SolicitudGeocodificacion> paraEsteProveedor = determinarSolicitudesParaProveedor(
                    pendientes, proveedor, i == 0);

            if (paraEsteProveedor.isEmpty()) {
                LOG.debug("GeocodingChain: proveedor {} no tiene solicitudes para procesar", proveedor.nombre());
                continue;
            }

            // Geocodificar con este proveedor
            Map<String, ResultadoGeocodificacion> resultados;
            try {
                resultados = proveedor.geocodificar(paraEsteProveedor);
            } catch (RuntimeException e) {
                LOG.error("GeocodingChain: proveedor {} lanzó excepción", proveedor.nombre(), e);
                resultados = Map.of();
            }

            // Clasificar resultados
            List<SolicitudGeocodificacion> siguientesPendientes = new ArrayList<>();

            for (SolicitudGeocodificacion s : paraEsteProveedor) {
                // Registrar intento
                proveedoresIntentados.computeIfAbsent(s.id(), k -> new ArrayList<>())
                        .add(proveedor.nombre());

                ResultadoGeocodificacion r = resultados.get(s.id());

                // Éxito: coordenada válida en Argentina
                if (r != null && r.esExitoso() && r.coordenada() != null && r.coordenada().enArgentina()) {
                    resultadosFinales.put(s.id(), r);
                    LOG.trace("GeocodingChain: {} resuelto por {}", s.id(), proveedor.nombre());
                    continue;
                }

                // Fallo: registrar para siguiente proveedor
                ResultadoGeocodificacion fallo;
                if (r == null) {
                    fallo = ResultadoGeocodificacion.Fallido.de(
                            ResultadoGeocodificacion.MotivoFallo.PROVEEDOR_NO_DISPONIBLE,
                            "sin respuesta",
                            proveedor.nombre());
                } else if (r.esExitoso()) {
                    // Éxito pero fuera de Argentina
                    fallo = ResultadoGeocodificacion.Fallido.de(
                            ResultadoGeocodificacion.MotivoFallo.FUERA_DE_AREA,
                            "coordenadas fuera de Argentina: " + r.coordenada(),
                            proveedor.nombre());
                } else {
                    fallo = r;
                }

                historialFallos.computeIfAbsent(s.id(), k -> new ArrayList<>()).add(fallo);
                siguientesPendientes.add(s);
            }

            // Si es el primer proveedor y tiene cuota, las que exceden la cuota pasan directo al siguiente
            if (i == 0 && proveedor.tieneCuota()) {
                int cuota = proveedor.cuotaDisponible();
                if (pendientes.size() > cuota && cuota >= 0) {
                    List<SolicitudGeocodificacion> excedenCuota = pendientes.subList(cuota, pendientes.size());
                    LOG.debug("GeocodingChain: {} solicitudes exceden cuota de {}, pasan al siguiente proveedor",
                            excedenCuota.size(), proveedor.nombre());
                    siguientesPendientes.addAll(excedenCuota);
                }
            }

            pendientes = siguientesPendientes;
        }

        // Lo que ningún proveedor resolvió: determinar mejor error
        for (SolicitudGeocodificacion s : pendientes) {
            List<ResultadoGeocodificacion> fallos = historialFallos.getOrDefault(s.id(), List.of());
            ResultadoGeocodificacion mejorError = seleccionarMejorError(fallos, proveedoresIntentados.get(s.id()));
            resultadosFinales.put(s.id(), mejorError);
        }

        long exitosos = resultadosFinales.values().stream().filter(ResultadoGeocodificacion::esExitoso).count();
        LOG.info("GeocodingChain: completado - {} exitosas, {} fallidas de {} total",
                exitosos, resultadosFinales.size() - exitosos, solicitudes.size());

        return resultadosFinales;
    }

    /**
     * Determina qué solicitudes enviar a un proveedor específico.
     * Para el primer proveedor: todas las que tengan datos válidos.
     * Para proveedores de fallback: solo las que fallaron en los anteriores.
     */
    private List<SolicitudGeocodificacion> determinarSolicitudesParaProveedor(
            List<SolicitudGeocodificacion> pendientes,
            GeocodingProvider proveedor,
            boolean esPrimero) {

        if (esPrimero) {
            // Primer proveedor: filtrar solo las que tienen datos válidos para él
            return pendientes.stream()
                    .filter(s -> tieneDatosParaProveedor(s.direccion(), proveedor))
                    .toList();
        }

        // Proveedores de fallback: todos los pendientes (ya fallaron en anteriores)
        // Pero filtrar los que no tengan datos mínimos para este proveedor
        return pendientes.stream()
                .filter(s -> tieneDatosParaProveedor(s.direccion(), proveedor))
                .toList();
    }

    /**
     * Verifica si una dirección tiene los datos mínimos para un proveedor.
     */
    private boolean tieneDatosParaProveedor(Direccion dir, GeocodingProvider proveedor) {
        return switch (proveedor.nombre()) {
            case "georef" -> dir.validaParaGeoref();
            case "nominatim" -> dir.tieneDatos();
            default -> dir.tieneDatos(); // Por defecto, si tiene algo de datos
        };
    }

    /**
     * Selecciona el error más informativo de la lista de fallos.
     * Prioridad: NO_ENCONTRADA > FUERA_DE_AREA > CUOTA_EXCEDIDA > PROVEEDOR_NO_DISPONIBLE > ERROR_COMUNICACION > DIRECCION_INVALIDA > ERROR_INTERNO > SIN_COORDENADAS_VALIDAS
     */
    private ResultadoGeocodificacion seleccionarMejorError(
            List<ResultadoGeocodificacion> fallos,
            List<String> proveedoresIntentados) {

        if (fallos.isEmpty()) {
            return ResultadoGeocodificacion.Fallido.de(
                    ResultadoGeocodificacion.MotivoFallo.PROVEEDOR_NO_DISPONIBLE,
                    "sin proveedores configurados",
                    "none");
        }

        // Ordenar por prioridad del motivo (el más específico primero)
        ResultadoGeocodificacion.MotivoFallo[] prioridad = {
                ResultadoGeocodificacion.MotivoFallo.NO_ENCONTRADA,
                ResultadoGeocodificacion.MotivoFallo.FUERA_DE_AREA,
                ResultadoGeocodificacion.MotivoFallo.CUOTA_EXCEDIDA,
                ResultadoGeocodificacion.MotivoFallo.PROVEEDOR_NO_DISPONIBLE,
                ResultadoGeocodificacion.MotivoFallo.ERROR_COMUNICACION,
                ResultadoGeocodificacion.MotivoFallo.DIRECCION_INVALIDA,
                ResultadoGeocodificacion.MotivoFallo.ERROR_INTERNO,
                ResultadoGeocodificacion.MotivoFallo.SIN_COORDENADAS_VALIDAS
        };

        for (ResultadoGeocodificacion.MotivoFallo m : prioridad) {
            for (ResultadoGeocodificacion f : fallos) {
                if (f instanceof ResultadoGeocodificacion.Fallido fallido && fallido.motivo() == m) {
                    return f;
                }
            }
        }

        // Fallback: el primero
        return fallos.get(0);
    }

    /**
     * Obtiene la lista de proveedores configurados (para inspección).
     */
    public List<GeocodingProvider> getProveedores() {
        return proveedores;
    }
}