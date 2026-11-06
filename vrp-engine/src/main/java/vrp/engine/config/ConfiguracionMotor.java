package vrp.engine.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import vrp.engine.solver.Objetivo;
import vrp.engine.solver.ParametrosSolver;

public record ConfiguracionMotor(
        String valhallaUrl,
        String osrmUrl,
        String valhallaCosting,
        int tamanoBloqueMatriz,
        int timeoutHttpSeg,
        Objetivo objetivo,
        int tiempoLimiteSolverSeg,
        long penalizacionBase,
        long coeficienteBalanceo) {

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

    /** Arma la configuración con los valores del archivo, variables de entorno y valores por defecto. */
    public static ConfiguracionMotor desdeProperties(Properties p) {
        return new ConfiguracionMotor(
                textoConEnv(p, "Valhalla.url", "VALHALLA_URL", null),
                textoConEnv(p, "OSRM.url", "OSRM_URL", null),
                textoConEnv(p, "Valhalla.costing", "VALHALLA_COSTING", "auto"),
                (int) numeroConEnv(p, "Matriz.tamanoBloque", "MATRIZ_TAMANO_BLOQUE", 50),
                (int) numeroConEnv(p, "Http.timeoutSeg", "HTTP_TIMEOUT_SEG", 30),
                Objetivo.valueOf(textoConEnv(p, "Solver.objetivo", "SOLVER_OBJETIVO", "DISTANCIA").toUpperCase()),
                (int) numeroConEnv(p, "Solver.tiempoLimiteSeg", "SOLVER_TIEMPO_LIMITE", 10),
                numeroConEnv(p, "Solver.penalizacionBase", "SOLVER_PENALIZACION_BASE", 10_000_000L),
                numeroConEnv(p, "Solver.coeficienteBalanceo", "SOLVER_COEFICIENTE_BALANCEO", 0));
    }

    /** Lee un texto desde properties, variable de entorno o usa el valor por defecto. */
    private static String textoConEnv(Properties p, String claveProps, String claveEnv, String defecto) {
        String v = p.getProperty(claveProps);
        if (v != null && !v.isBlank()) return v.trim();
        String env = System.getenv(claveEnv);
        if (env != null && !env.isBlank()) return env.trim();
        return defecto;
    }

    /** Lee un número entero desde properties, variable de entorno o usa el valor por defecto. */
    private static long numeroConEnv(Properties p, String claveProps, String claveEnv, long defecto) {
        String v = p.getProperty(claveProps);
        if (v != null && !v.isBlank()) return Long.parseLong(v.trim());
        String env = System.getenv(claveEnv);
        if (env != null && !env.isBlank()) return Long.parseLong(env.trim());
        return defecto;
    }
}