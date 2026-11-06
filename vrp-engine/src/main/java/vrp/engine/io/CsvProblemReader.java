package vrp.engine.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public final class CsvProblemReader extends TablaProblemReader {

    /** Reconoce archivos .csv. */
    @Override
    public boolean soporta(Path archivo) {
        return archivo.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    /** Lee el CSV usando la primera línea como encabezado. */
    @Override
    protected List<Map<String, String>> leerFilas(Path archivo) throws IOException {
        String texto = Files.readString(archivo, StandardCharsets.UTF_8);
        if (texto.startsWith("\uFEFF")) texto = texto.substring(1);
        String primeraLinea = texto.lines().findFirst().orElse("");
        // El delimitador se detecta por la primera línea.
        char delimitador = primeraLinea.contains(";") ? ';' : ',';

        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimitador)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<Map<String, String>> filas = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new java.io.StringReader(texto));
             CSVParser parser = formato.parse(br)) {
            for (CSVRecord registro : parser) {
                filas.add(new LinkedHashMap<>(registro.toMap()));
            }
        }
        return filas;
    }
}
