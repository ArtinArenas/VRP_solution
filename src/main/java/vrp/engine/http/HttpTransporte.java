package vrp.engine.http;

import java.io.IOException;
import java.net.URI;

public interface HttpTransporte {

    /** Hace un GET y devuelve el cuerpo de la respuesta. */
    String get(URI uri) throws IOException;

    /** Hace un POST con cuerpo JSON y devuelve el cuerpo de la respuesta. */
    String post(URI uri, String cuerpoJson) throws IOException;
}
