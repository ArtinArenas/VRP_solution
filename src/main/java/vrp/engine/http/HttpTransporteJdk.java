package vrp.engine.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class HttpTransporteJdk implements HttpTransporte {

    private final HttpClient client;
    private final Duration timeout;
    private final String userAgent;

    public HttpTransporteJdk(HttpClient client, Duration timeout, String userAgent) {
        this.client = client;
        this.timeout = timeout;
        this.userAgent = userAgent;
    }

    /** Crea el transporte con timeout y un User-Agent que identifica al motor. */
    public static HttpTransporteJdk porDefecto(int timeoutSeg, String contacto) {
        String ua = "VRPEngine/1.0" + (contacto == null ? "" : " (contacto: " + contacto + ")");
        return new HttpTransporteJdk(HttpClient.newHttpClient(), Duration.ofSeconds(timeoutSeg), ua);
    }

    /** GET con los encabezados comunes. */
    @Override
    public String get(URI uri) throws IOException {
        return enviar(base(uri).GET().build());
    }

    /** POST de un cuerpo JSON con los encabezados comunes. */
    @Override
    public String post(URI uri, String cuerpoJson) throws IOException {
        return enviar(base(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpoJson, StandardCharsets.UTF_8))
                .build());
    }

    /** Solicitud base con timeout, User-Agent y Accept. */
    private HttpRequest.Builder base(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json");
    }

    /** Envía la solicitud y devuelve el cuerpo; falla si la respuesta no es 2xx. */
    private String enviar(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> r = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // Cualquier código fuera de 2xx se informa como error de comunicación.
            if (r.statusCode() < 200 || r.statusCode() > 299) {
                String body = r.body() == null ? "" : r.body();
                throw new IOException("HTTP " + r.statusCode() + " en " + request.uri().getHost() + ": "
                        + body.substring(0, Math.min(body.length(), 300)));
            }
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Solicitud interrumpida", e);
        }
    }
}
