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
     * Lee un archivo y devuelve las direcciones encontradas.
     * Formatos soportados: XML, CSV, XLS y XLSX.
     */
    public static List<String> read(String filePath)
            throws Exception {

        Path path = Paths.get(filePath);

        if (!Files.isRegularFile(path)) {
            throw new IOException(
                    "El archivo no existe: " + filePath
            );
        }

        String fileName = path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        if (fileName.endsWith(".csv")) {
            return readCsv(path);
        }

        if (fileName.endsWith(".xml")) {
            return readXml(path);
        }

        if (fileName.endsWith(".xls")
                || fileName.endsWith(".xlsx")) {
            return readExcel(path);
        }

        throw new IllegalArgumentException(
                "Formato no soportado: " + fileName
        );
    }

    /**
     * Lee un CSV suponiendo una dirección por línea.
     * Conserva las comas que forman parte de la dirección.
     */
    private static List<String> readCsv(Path path)
            throws IOException {

        List<String> addresses = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(
                path, StandardCharsets.UTF_8)) {

            String line;

            while ((line = reader.readLine()) != null) {
                String address = line.trim();

                // Elimina comillas externas opcionales.
                if (address.length() >= 2
                        && address.startsWith("\"")
                        && address.endsWith("\"")) {

                    address = address.substring(
                            1, address.length() - 1
                    ).replace("\"\"", "\"");
                }

                addAddress(addresses, address);
            }
        }

        return addresses;
    }

    /**
     * Lee los valores de los nodos hoja de un XML.
     *
     * Ejemplo:
     * <direcciones>
     *   <direccion>Av. Mitre 123, Burzaco</direccion>
     * </direcciones>
     */
    private static List<String> readXml(Path path)
            throws Exception {

        List<String> addresses = new ArrayList<>();

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        // Evita que el XML acceda a entidades externas.
        factory.setFeature(
                XMLConstants.FEATURE_SECURE_PROCESSING, true
        );
        factory.setFeature(
                "http://apache.org/xml/features/"
                        + "disallow-doctype-decl", true
        );
        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_DTD, ""
        );
        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""
        );
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

            // Solo toma nodos hoja para evitar duplicar
            // el contenido de elementos contenedores.
            if (!node.hasChildNodes()) {
                continue;
            }

            boolean hasElementChild = false;
            NodeList children = node.getChildNodes();

            for (int j = 0; j < children.getLength(); j++) {
                if (children.item(j).getNodeType()
                        == Node.ELEMENT_NODE) {
                    hasElementChild = true;
                    break;
                }
            }

            if (!hasElementChild) {
                addAddress(addresses, node.getTextContent());
            }
        }

        return addresses;
    }

    /**
     * Lee la primera columna de la primera hoja de Excel.
     * Compatible con XLS y XLSX.
     */
    private static List<String> readExcel(Path path)
            throws IOException {

        List<String> addresses = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream input = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(input)) {

            if (workbook.getNumberOfSheets() == 0) {
                return addresses;
            }

            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                Cell cell = row.getCell(
                        0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                );

                if (cell == null) {
                    continue;
                }

                String address = formatter.formatCellValue(cell);
                addAddress(addresses, address);
            }
        }

        return addresses;
    }

    /**
     * Limpia espacios y descarta valores vacíos.
     */
    private static void addAddress(
            List<String> addresses, String value) {

        if (value == null) {
            return;
        }

        String address = value.trim();

        if (!address.isEmpty()) {
            addresses.add(address);
        }
    }
}

