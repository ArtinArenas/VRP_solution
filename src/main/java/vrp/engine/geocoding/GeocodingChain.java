package vrp.engine.geocoding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeocodingChain {

    private final List<GeocodingProvider> proveedores;
    private static final int GEOREF_CUOTA_DIARIA = 1000;

    public GeocodingChain(List<GeocodingProvider> proveedores) {
        this.proveedores = List.copyOf(proveedores);
    }

    /** Resuelve las direcciones probando los proveedores en orden de preferencia.
     * Si el primer proveedor es Georef y hay más de 1000 direcciones, solo las primeras 1000 van a Georef;
     * las restantes van directamente al siguiente proveedor (Nominatim) para respetar la cuota diaria. */
    public Map<String, ResolucionGeo> resolver(List<SolicitudGeocodificacion> solicitudes) {
        Map<String, ResolucionGeo> salida = new LinkedHashMap<>();
        Map<String, List<String>> intentados = new LinkedHashMap<>();
        Map<String, List<ResultadoGeo>> fallos = new LinkedHashMap<>();
        Map<String, List<String>> detalles = new LinkedHashMap<>();

        List<SolicitudGeocodificacion> pendientes = new ArrayList<>(solicitudes);

        // Cada proveedor solo recibe lo que los anteriores no pudieron resolver.
        for (int i = 0; i < proveedores.size(); i++) {
            GeocodingProvider proveedor = proveedores.get(i);
            if (pendientes.isEmpty()) break;

            // Si es Georef (primer proveedor) y hay más de la cuota, solo procesamos hasta la cuota
            List<SolicitudGeocodificacion> paraEsteProveedor = pendientes;
            if (i == 0 && "georef".equalsIgnoreCase(proveedor.nombre()) && pendientes.size() > GEOREF_CUOTA_DIARIA) {
                paraEsteProveedor = pendientes.subList(0, GEOREF_CUOTA_DIARIA);
                // Las que no entran en la cuota de Georef pasan directamente al siguiente proveedor
                List<SolicitudGeocodificacion> excedenCuota = pendientes.subList(GEOREF_CUOTA_DIARIA, pendientes.size());
                // Las procesamos después con el siguiente proveedor
            }

            Map<String, ResultadoGeo> respuestas;
            try {
                respuestas = proveedor.geocodificar(paraEsteProveedor);
            } catch (RuntimeException e) {
                respuestas = Map.of();
            }

            List<SolicitudGeocodificacion> siguientes = new ArrayList<>();
            for (SolicitudGeocodificacion s : paraEsteProveedor) {
                List<String> lista = intentados.computeIfAbsent(s.id(), k -> new ArrayList<>());
                lista.add(proveedor.nombre());

                ResultadoGeo r = respuestas.get(s.id());
                // Un resultado fuera de Argentina se trata como falla y pasa al siguiente proveedor.
                if (r != null && r.exito() && LimitesArgentina.contiene(r.coordenada())) {
                    salida.put(s.id(), new ResolucionGeo(s.id(), r, List.copyOf(lista), proveedor.nombre()));
                    continue;
                }

                ResultadoGeo fallo;
                if (r == null) {
                    fallo = ResultadoGeo.fallo(MotivoGeo.PROVEEDOR_NO_DISPONIBLE, "sin respuesta");
                } else if (r.exito()) {
                    fallo = ResultadoGeo.fallo(MotivoGeo.FUERA_DE_ARGENTINA,
                            "coordenadas fuera de Argentina: " + r.coordenada());
                } else {
                    fallo = r;
                }
                fallos.computeIfAbsent(s.id(), k -> new ArrayList<>()).add(fallo);
                detalles.computeIfAbsent(s.id(), k -> new ArrayList<>())
                        .add(proveedor.nombre() + ": " + fallo.detalle());
                siguientes.add(s);
            }

            // Agregar las que exceden la cuota de Georef a las pendientes del siguiente proveedor
            if (i == 0 && "georef".equalsIgnoreCase(proveedor.nombre()) && pendientes.size() > GEOREF_CUOTA_DIARIA) {
                siguientes.addAll(pendientes.subList(GEOREF_CUOTA_DIARIA, pendientes.size()));
            }

            pendientes = siguientes;
        }

        // Lo que ningún proveedor resolvió queda como error, con el motivo más informativo.
        for (SolicitudGeocodificacion s : pendientes) {
            List<ResultadoGeo> lista = fallos.getOrDefault(s.id(), List.of());
            MotivoGeo motivo = lista.stream()
                    .map(ResultadoGeo::motivo)
                    .min(Comparator.naturalOrder())
                    .orElse(MotivoGeo.PROVEEDOR_NO_DISPONIBLE);
            String detalle = detalles.isEmpty() || !detalles.containsKey(s.id())
                    ? "sin proveedores configurados"
                    : String.join(" | ", detalles.get(s.id()));
            salida.put(s.id(), new ResolucionGeo(s.id(), ResultadoGeo.fallo(motivo, detalle),
                    List.copyOf(intentados.getOrDefault(s.id(), List.of())), null));
        }
        return salida;
    }
}
