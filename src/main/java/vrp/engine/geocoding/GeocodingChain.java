package vrp.engine.geocoding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeocodingChain {

    private final List<GeocodingProvider> proveedores;

    public GeocodingChain(List<GeocodingProvider> proveedores) {
        this.proveedores = List.copyOf(proveedores);
    }

    /** Resuelve las direcciones probando los proveedores en orden de preferencia. */
    public Map<String, ResolucionGeo> resolver(List<SolicitudGeocodificacion> solicitudes) {
        Map<String, ResolucionGeo> salida = new LinkedHashMap<>();
        Map<String, List<String>> intentados = new LinkedHashMap<>();
        Map<String, List<ResultadoGeo>> fallos = new LinkedHashMap<>();
        Map<String, List<String>> detalles = new LinkedHashMap<>();
        List<SolicitudGeocodificacion> pendientes = new ArrayList<>(solicitudes);

        // Cada proveedor solo recibe lo que los anteriores no pudieron resolver.
        for (GeocodingProvider proveedor : proveedores) {
            if (pendientes.isEmpty()) break;

            Map<String, ResultadoGeo> respuestas;
            try {
                respuestas = proveedor.geocodificar(pendientes);
            } catch (RuntimeException e) {
                respuestas = Map.of();
            }

            List<SolicitudGeocodificacion> siguientes = new ArrayList<>();
            for (SolicitudGeocodificacion s : pendientes) {
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
