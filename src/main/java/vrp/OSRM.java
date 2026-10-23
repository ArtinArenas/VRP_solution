package vrp;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;

public class OSRM {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final String API_URL;

    static {
        Properties config = new Properties();
        try (FileInputStream file = new FileInputStream("config.properties")) {
            config.load(file);
            API_URL = config.getProperty("OSRM.url");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static String getMatriz(List<? extends Direccion> direcciones)
            throws IOException, InterruptedException {

        if (direcciones == null || direcciones.isEmpty()) {
            throw new IllegalArgumentException(
                    "La lista de direcciones no puede estar vacía");
        }

        StringBuilder lista = new StringBuilder();
        for (Direccion d : direcciones) {
            if (lista.length() > 0) lista.append(";");
            lista.append(d.getLon()).append(",").append(d.getLat());
        }

        String url = API_URL
                + "/table/v1/driving/"
                + lista
                + "?annotations=distance,duration";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Error HTTP OSRM: "
                    + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    public static void getRoute(List<? extends Cliente> clientes)
            throws IOException, InterruptedException {
        if (clientes == null || clientes.isEmpty()) {
            throw new IllegalArgumentException();
        }


    }
}