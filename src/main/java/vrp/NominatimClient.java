package vrp;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.util.Properties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NominatimClient {

    private static final HttpClient client = HttpClient.newHttpClient();

    private static final String API_URL;
    private static final String EMAIL;

    // Se ejecuta una sola vez al inicializar la clase
    static {
        Properties config = new Properties();

        try (FileInputStream file =
                     new FileInputStream("config.properties")) {

            config.load(file);

            API_URL = config.getProperty("Nominatim.url");
            EMAIL = config.getProperty("Nominatim.email");

        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static Direccion getDireccion(String direccion) throws IOException, InterruptedException {
        Direccion resp = new Direccion();

        String direccionCodificada = URLEncoder.encode(
                direccion, StandardCharsets.UTF_8
        );

        String url = API_URL
                + "/search"
                + "?q=" + direccionCodificada
                + "&format=jsonv2"
                + "&limit=1";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "MiAplicacionJava/1.0 (contacto: " + EMAIL + ")")
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
            );
        }

        //Transformo él response en un objeto Direccion
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // Leer como array debido a los corchetes [ ]
            Direccion[] direcciones = objectMapper.readValue(response.body(), Direccion[].class);

            if (direcciones.length > 0) {
                resp = direcciones[0];
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return resp;
    }
}