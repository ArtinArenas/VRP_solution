package vrp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AddressFileReader {

    /**
     * Lee un archivo y devuelve los clientes encontrados.
     * Cada registro tiene el formato: nombreCliente;direccion
     * Formatos soportados: XML, CSV, XLS y XLSX.
     */
    public static List<Cliente> read(String filePath) throws Exception {
        Path path = Paths.get(filePath);

        if (!Files.isRegularFile(path)) {
            throw new IOException("El archivo no existe: " + filePath);
        }

        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

        if (fileName.endsWith(".csv"))  return readCsv(path);
        if (fileName.endsWith(".xml"))  return readXml(path);
        if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) return readExcel(path);

        throw new IllegalArgumentException("Formato no soportado: " + fileName);
    }

    /** CSV: una entrada por línea con formato nombreCliente;direccion. */
    private static List<Cliente> readCsv(Path path) throws IOException {
        List<Cliente> clientes = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();

                // Quita comillas externas opcionales.
                if (value.length() >= 2
                        && value.startsWith("\"")
                        && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1)
                            .replace("\"\"", "\"");
                }

                Cliente c = parseCliente(value);
                if (c != null) clientes.add(c);
            }
        }
        return clientes;
    }

    /** XML: cada nodo hoja debe contener "nombreCliente;direccion". */
    private static List<Cliente> readXml(Path path) throws Exception {
        List<Cliente> clientes = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document;
        try (InputStream input = Files.newInputStream(path)) {
            document = builder.parse(input);
        }

        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);

            if (!node.hasChildNodes()) continue;

            boolean hasElementChild = false;
            NodeList children = node.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                if (children.item(j).getNodeType() == Node.ELEMENT_NODE) {
                    hasElementChild = true;
                    break;
                }
            }

            if (!hasElementChild) {
                Cliente c = parseCliente(node.getTextContent());
                if (c != null) clientes.add(c);
            }
        }
        return clientes;
    }

    /**
     * Excel: primera hoja.
     *  - Si la fila tiene contenido en la columna 1 → col0 = nombre, col1 = dirección.
     *  - Si no → se espera "nombre;direccion" en la columna 0.
     */
    private static List<Cliente> readExcel(Path path) throws IOException {
        List<Cliente> clientes = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream input = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(input)) {

            if (workbook.getNumberOfSheets() == 0) return clientes;

            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                Cell c0 = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (c0 == null) continue;

                String v0 = formatter.formatCellValue(c0).trim();
                if (v0.isEmpty()) continue;

                Cell c1 = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String v1 = (c1 == null) ? "" : formatter.formatCellValue(c1).trim();

                if (!v1.isEmpty()) {
                    clientes.add(new Cliente(v0, v1));
                } else {
                    Cliente c = parseCliente(v0);
                    if (c != null) clientes.add(c);
                }
            }
        }
        return clientes;
    }

    /**
     * Convierte "nombre;direccion" en un Cliente.
     * Si no hay ';' se toma todo el texto como dirección.
     */
    private static Cliente parseCliente(String value) {
        if (value == null) return null;

        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;

        int idx = trimmed.indexOf(';');
        if (idx < 0) {
            return new Cliente("", trimmed);
        }

        String nombre    = trimmed.substring(0, idx).trim();
        String direccion = trimmed.substring(idx + 1).trim();

        if (direccion.isEmpty()) return null;
        return new Cliente(nombre, direccion);
    }
}