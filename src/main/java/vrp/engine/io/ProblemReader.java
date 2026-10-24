package vrp.engine.io;

import java.io.IOException;
import java.nio.file.Path;
import vrp.engine.model.ContextoOperativo;

public interface ProblemReader {

    /** Indica si el lector entiende la extensión del archivo. */
    boolean soporta(Path archivo);

    /** Lee el archivo y devuelve el problema junto con los errores encontrados. */
    ResultadoLectura leer(Path archivo, ContextoOperativo contexto) throws IOException;
}
