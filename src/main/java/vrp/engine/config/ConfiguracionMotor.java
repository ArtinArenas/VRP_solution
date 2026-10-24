package vrp.engine.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import vrp.engine.solver.Objetivo;
import vrp.engine.solver.ParametrosSolver;

public record ConfiguracionMotor(
        String georefUrl,
        String nominatimUrl,
        String nominatimEmail,
        long nominatimPausaMs,
        String valhallaUrl,
        String osrmUrl,
        String valhallaCosting,
        int tamanoBloqueMatriz,
        int timeoutHttpSeg,
        Objetivo objetivo,
        int tiempoLimiteSolverSeg,
        long penalizacionBase,
        long coeficienteBalanceo) {

    public static final String GEOREF_URL_DEFECTO = "https://apis.datos.gob.ar/georef/api/v2.0";

    /** Extrae los parámetros que usa el solver. */
    public ParametrosSolver parametrosSolver() {
        return new ParametrosSolver(objetivo, tiempoLimiteSolverSeg, penalizacionBase, coeficienteBalanceo);
    }

    /** Carga la configuración desde un archivo .properties. */
    public static ConfiguracionMotor desdeArchivo(Path archivo) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(archivo)) {
            p.load(in);
        }
        return desdeProperties(p);
    }

    /** Arma la configuración con los valores del archivo y los valores por defecto. */
    public static ConfiguracionMotor desdeProperties(Properties p) {
        return new ConfiguracionMotor(
                texto(p, "Georef.url", GEOREF_URL_DEFECTO),
                texto(p, "Nominatim.url", null),
                texto(p, "Nominatim.email", null),
                numero(p, "Nominatim.pausaMs", 0),
                texto(p, "Valhalla.url", null),
                texto(p, "OSRM.url", null),
                texto(p, "Valhalla.costing", "auto"),
                (int) numero(p, "Matriz.tamanoBloque", 50),
                (int) numero(p, "Http.timeoutSeg", 30),
                Objetivo.valueOf(texto(p, "Solver.objetivo", "DISTANCIA").toUpperCase()),
                (int) numero(p, "Solver.tiempoLimiteSeg", 10),
                numero(p, "Solver.penalizacionBase", 10_000_000L),
                numero(p, "Solver.coeficienteBalanceo", 0));
    }

    /** Lee un texto; si falta o está vacío usa el valor por defecto. */
    private static String texto(Properties p, String clave, String defecto) {
        String v = p.getProperty(clave);
        return v == null || v.isBlank() ? defecto : v.trim();
    }

    /** Lee un número entero; si falta o está vacío usa el valor por defecto. */
    private static long numero(Properties p, String clave, long defecto) {
        String v = p.getProperty(clave);
        return v == null || v.isBlank() ? defecto : Long.parseLong(v.trim());
    }
}
