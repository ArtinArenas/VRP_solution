package vrp.engine.io;

import java.nio.file.Path;
import java.util.List;

public final class ProblemReaderFactory {

    private static final List<ProblemReader> LECTORES = List.of(
            new JsonProblemReader(),
            new CsvProblemReader(),
            new ExcelProblemReader(),
            new XmlProblemReader());

    private ProblemReaderFactory() {
    }

    /** Elige el lector según la extensión del archivo. */
    public static ProblemReader para(Path archivo) {
        return LECTORES.stream()
                .filter(l -> l.soporta(archivo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Formato no soportado: " + archivo.getFileName()));
    }
}
