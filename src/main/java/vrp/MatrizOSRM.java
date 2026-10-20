package vrp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class MatrizOSRM {

    private final double[][] distancias;
    private final long[][] tiempos;

    public MatrizOSRM(double[][] distancias, long[][] tiempos) {
        this.distancias = distancias;
        this.tiempos = tiempos;
    }

    public double[][] getDistancias() {
        return distancias;
    }

    public long[][] getTiempos() {
        return tiempos;
    }

    //Función que recibe los datos desde un json y arma las matrices necesarias
    public static MatrizOSRM procesarMatriz(String body)
            throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(body);

        String codigo = root.path("code").asText();

        if (!"Ok".equals(codigo)) {
            throw new IOException(
                    "OSRM no pudo calcular la matriz: "
                            + root.path("message").asText(codigo)
            );
        }

        JsonNode distanciasJson = root.path("distances");
        JsonNode tiemposJson = root.path("durations");

        if (!distanciasJson.isArray() || !tiemposJson.isArray()) {
            throw new IOException(
                    "La respuesta de OSRM no contiene ambas matrices"
            );
        }

        int n = distanciasJson.size();

        if (tiemposJson.size() != n) {
            throw new IOException("Dimensiones de matrices incompatibles");
        }

        double[][] distancias = new double[n][n];
        long[][] tiempos = new long[n][n];

        for (int i = 0; i < n; i++) {

            JsonNode filaDistancia = distanciasJson.get(i);
            JsonNode filaTiempo = tiemposJson.get(i);

            if (filaDistancia.size() != n || filaTiempo.size() != n) {
                throw new IOException(
                        "La matriz recibida no es cuadrada"
                );
            }

            for (int j = 0; j < n; j++) {

                JsonNode distancia = filaDistancia.get(j);
                JsonNode tiempo = filaTiempo.get(j);

                // OSRM devuelve null cuando no existe una ruta.
                if (distancia == null || distancia.isNull()
                        || tiempo == null || tiempo.isNull()) {
                    throw new IOException(
                            "No existe ruta entre los nodos "
                                    + i + " y " + j
                    );
                }

                distancias[i][j] = distancia.asDouble();

                // Convierte segundos a enteros para OR-Tools.
                tiempos[i][j] = Math.round(tiempo.asDouble());
            }
        }

        return new MatrizOSRM(distancias, tiempos);
    }

}
