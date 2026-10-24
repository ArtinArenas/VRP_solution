package vrp.engine.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public final class ExcelProblemReader extends TablaProblemReader {

    /** Reconoce archivos .xls y .xlsx. */
    @Override
    public boolean soporta(Path archivo) {
        String n = archivo.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".xls") || n.endsWith(".xlsx");
    }

    /** Lee la primera hoja; la primera fila son los encabezados y las filas vacías se saltean. */
    @Override
    protected List<Map<String, String>> leerFilas(Path archivo) throws IOException {
        DataFormatter formateador = new DataFormatter();
        List<Map<String, String>> filas = new ArrayList<>();

        try (InputStream in = Files.newInputStream(archivo); Workbook libro = WorkbookFactory.create(in)) {
            if (libro.getNumberOfSheets() == 0) return filas;
            Sheet hoja = libro.getSheetAt(0);
            // La primera fila define los nombres de las columnas.
            Row encabezado = hoja.getRow(hoja.getFirstRowNum());
            if (encabezado == null) return filas;

            List<String> columnas = new ArrayList<>();
            for (int c = 0; c < encabezado.getLastCellNum(); c++) {
                Cell cell = encabezado.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                columnas.add(cell == null ? "" : formateador.formatCellValue(cell).trim());
            }

            for (int r = hoja.getFirstRowNum() + 1; r <= hoja.getLastRowNum(); r++) {
                Row fila = hoja.getRow(r);
                if (fila == null) continue;
                Map<String, String> valores = new LinkedHashMap<>();
                boolean contenido = false;
                for (int c = 0; c < columnas.size(); c++) {
                    Cell cell = fila.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String v = cell == null ? "" : formateador.formatCellValue(cell).trim();
                    if (!v.isEmpty()) contenido = true;
                    if (!columnas.get(c).isEmpty()) valores.put(columnas.get(c), v);
                }
                if (contenido) filas.add(valores);
            }
        }
        return filas;
    }
}
