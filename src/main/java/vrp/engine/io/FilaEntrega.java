package vrp.engine.io;

import java.text.Normalizer;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import vrp.engine.model.Coordenada;
import vrp.engine.model.DireccionPostal;
import vrp.engine.model.Entrega;
import vrp.engine.model.VentanaHoraria;
import vrp.engine.resultado.ErrorLectura;
import vrp.engine.util.Tiempos;

final class FilaEntrega {

    record Resultado(Entrega entrega, ErrorLectura error) {
    }

    private FilaEntrega() {
    }

    /** Normaliza un encabezado: minúsculas, sin tildes ni símbolos. */
    static String clave(String encabezado) {
        if (encabezado == null) return "";
        String s = Normalizer.normalize(encabezado.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return s.replaceAll("[^a-z0-9]", "");
    }

    /** Normaliza los encabezados de una fila y los valores. */
    static Map<String, String> normalizar(Map<String, String> fila) {
        Map<String, String> out = new HashMap<>();
        fila.forEach((k, v) -> out.put(clave(k), v == null ? "" : v.trim()));
        return out;
    }

    /** Convierte una fila en una entrega, o en un error si algún valor no es válido. */
    static Resultado mapear(Map<String, String> filaCruda, int numeroFila) {
        Map<String, String> f = normalizar(filaCruda);
        String id = valor(f, "id");
        String referencia = id.isEmpty() ? "fila " + numeroFila : id;
        // Sin id en el archivo se genera uno según el número de fila.
        if (id.isEmpty()) id = "E" + numeroFila;

        try {
            String direccion = valor(f, "direccion", "calle");
            String localidad = valor(f, "localidad", "ciudad");
            String provincia = valor(f, "provincia");

            Coordenada coordenada = null;
            String lat = valor(f, "lat", "latitud");
            String lon = valor(f, "lon", "lng", "longitud");
            if (!lat.isEmpty() && !lon.isEmpty()) {
                coordenada = new Coordenada(numero(lat), numero(lon));
            }

            VentanaHoraria ventana = null;
            String desde = valor(f, "ventanadesde", "desde");
            String hasta = valor(f, "ventanahasta", "hasta");
            if (!desde.isEmpty() || !hasta.isEmpty()) {
                LocalTime d = desde.isEmpty() ? LocalTime.MIN : Tiempos.parseHora(desde);
                LocalTime h = hasta.isEmpty() ? LocalTime.of(23, 59, 59) : Tiempos.parseHora(hasta);
                ventana = new VentanaHoraria(d, h);
            }

            Entrega e = new Entrega(id, valor(f, "nombre", "cliente"),
                    new DireccionPostal(direccion, localidad, provincia), coordenada,
                    entero(valor(f, "demanda", "carga", "peso")),
                    entero(valor(f, "serviciomin", "servicio", "tiempodeservicio")),
                    ventana, entero(valor(f, "prioridad")));
            return new Resultado(e, null);
        } catch (RuntimeException ex) {
            return new Resultado(null, new ErrorLectura(referencia, "Fila invalida: " + ex.getMessage()));
        }
    }

    /** Primer valor no vacío entre las columnas alternativas. */
    private static String valor(Map<String, String> f, String... claves) {
        for (String c : claves) {
            String v = f.get(c);
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    /** Convierte a entero; vacío equivale a 0. */
    private static int entero(String s) {
        return s.isEmpty() ? 0 : (int) Math.round(numero(s));
    }

    /** Convierte a número aceptando coma o punto decimal. */
    private static double numero(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }
}
