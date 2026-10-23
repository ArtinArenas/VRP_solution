package vrp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class NominatimClient {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String API_URL;
    private static final String EMAIL;

    static {
        Properties config = new Properties();
        try (FileInputStream file = new FileInputStream("config.properties")) {
            config.load(file);
            API_URL = config.getProperty("Nominatim.url");
            EMAIL   = config.getProperty("Nominatim.email");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Geocodifica la dirección del cliente.
     *  - Entrada: cliente.getName()  →  texto de la dirección.
     *  - Salida:  cliente.lat / lon / display_name  completados.
     *             cliente.getName() se PRESERVA (dirección original del archivo).
     *
     * @return el mismo Cliente, ya geocodificado.
     */
    public static Cliente geocodificar(Cliente cliente)
            throws IOException, InterruptedException {

        String direccion = cliente.getName();

        if (direccion == null || direccion.isBlank()) {
            throw new IllegalArgumentException(
                    "El cliente '" + cliente.getNombre() + "' no tiene dirección");
        }

        String codificada = URLEncoder.encode(direccion, StandardCharsets.UTF_8);

        String url = API_URL
                + "/search"
                + "?q=" + codificada
                + "&format=jsonv2"
                + "&limit=1";

        System.out.println("\nURL a consultar: " + url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent",
                            "TSP_test/1.0 (contacto: " + EMAIL + ")")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println("\nURL a consultar: ");
            throw new IOException("Error HTTP Nominatim: " + response.statusCode());
        }

        // Nominatim devuelve un array; tomamos el primer resultado.
        Direccion[] resultados =
                mapper.readValue(response.body(), Direccion[].class);

        if (resultados.length == 0) {
            throw new IOException(
                    "Nominatim no encontró resultado para: " + direccion);
        }

        Direccion r = resultados[0];

        // Copiamos SOLO los datos geocodificados.
        // cliente.name se mantiene con el texto original del archivo.
        cliente.setLat(r.getLat());
        cliente.setLon(r.getLon());
        cliente.setDisplay_name(r.getDisplay_name());

        return cliente;
        }
        catch (Exception e) {
            System.out.println("\nNo se pudo enviar la petición: " + e.getMessage());
            throw new ExceptionInInitializerError(e);
        }
    }
}