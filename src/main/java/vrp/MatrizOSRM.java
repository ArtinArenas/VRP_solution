package vrp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class MatrizOSRM {

    private final double[][] distancias;
    private final long[][]   tiempos;

    public MatrizOSRM(double[][] distancias, long[][] tiempos) {
        this.distancias = distancias;
        this.tiempos    = tiempos;
    }

    public double[][] getDistancias() { return distancias; }
    public long[][]   getTiempos()    { return tiempos; }
    public int        size()          { return distancias.length; }

    /** Procesa la respuesta JSON de OSRM /table. */
    public static MatrizOSRM procesarMatriz(String body) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(body);

        String codigo = root.path("code").asText();
        if (!"Ok".equals(codigo)) {
            throw new IOException("OSRM no pudo calcular la matriz: "
                    + root.path("message").asText(codigo));
        }

        JsonNode distanciasJson = root.path("distances");
        JsonNode tiemposJson    = root.path("durations");

        if (!distanciasJson.isArray() || !tiemposJson.isArray()) {
            throw new IOException(
                    "La respuesta de OSRM no contiene ambas matrices");
        }

        int n = distanciasJson.size();
        if (tiemposJson.size() != n) {
            throw new IOException("Dimensiones de matrices incompatibles");
        }

        double[][] distancias = new double[n][n];
        long[][]   tiempos    = new long[n][n];

        for (int i = 0; i < n; i++) {
            JsonNode filaD = distanciasJson.get(i);
            JsonNode filaT = tiemposJson.get(i);

            if (filaD.size() != n || filaT.size() != n) {
                throw new IOException("La matriz recibida no es cuadrada");
            }

            for (int j = 0; j < n; j++) {
                JsonNode d = filaD.get(j);
                JsonNode t = filaT.get(j);

                if (d == null || d.isNull() || t == null || t.isNull()) {
                    throw new IOException(
                            "No existe ruta entre los nodos " + i + " y " + j);
                }

                distancias[i][j] = d.asDouble();
                tiempos[i][j]    = Math.round(t.asDouble());
            }
        }

        return new MatrizOSRM(distancias, tiempos);
    }
}