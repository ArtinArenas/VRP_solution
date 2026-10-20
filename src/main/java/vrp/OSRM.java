package vrp;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.List;

public class OSRM {
    private static final HttpClient client = HttpClient.newHttpClient();

    private static final String API_URL;

    // Se ejecuta una sola vez al inicializar la clase
    static {
        Properties config = new Properties();

        try (FileInputStream file = new FileInputStream("config.properties")) {
            config.load(file);
            API_URL = config.getProperty("OSRM.url");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }


    public static String getMatriz(List<Direccion> direcciones)
            throws IOException, InterruptedException {

        if (direcciones == null || direcciones.isEmpty()) {
            throw new IllegalArgumentException(
                    "La lista de direcciones no puede estar vacía"
            );
        }

        StringBuilder listaDirecciones = new StringBuilder();

        for (Direccion direccion : direcciones) {
            if (!listaDirecciones.isEmpty()) {
                listaDirecciones.append(";");
            }

            listaDirecciones
                    .append(direccion.getLon())
                    .append(",")
                    .append(direccion.getLat());
        }

        String url = API_URL
                + "/table/v1/driving/"
                + listaDirecciones
                + "?annotations=distance,duration";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Error HTTP: " + response.statusCode()
                            + " - " + response.body()
            );
        }

        return response.body();
    }

}
