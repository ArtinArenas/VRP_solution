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
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class XmlProblemReader extends TablaProblemReader {

    /** Reconoce archivos .xml. */
    @Override
    public boolean soporta(Path archivo) {
        return archivo.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    /** Toma como registro cada elemento cuyos hijos son campos simples. */
    @Override
    protected List<Map<String, String>> leerFilas(Path archivo) throws IOException {
        Document documento;
        try (InputStream in = Files.newInputStream(archivo)) {
            documento = fabrica().newDocumentBuilder().parse(in);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("XML invalido: " + e.getMessage(), e);
        }

        List<Map<String, String>> filas = new ArrayList<>();
        NodeList todos = documento.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            Element elemento = (Element) todos.item(i);
            Map<String, String> campos = camposHoja(elemento);
            if (campos != null) filas.add(campos);
        }
        return filas;
    }

    /** Campos de un elemento, o null si no es un registro (tiene hijos anidados o menos de 2 campos). */
    private static Map<String, String> camposHoja(Element elemento) {
        Map<String, String> campos = new LinkedHashMap<>();
        NodeList hijos = elemento.getChildNodes();
        for (int j = 0; j < hijos.getLength(); j++) {
            Node hijo = hijos.item(j);
            if (hijo.getNodeType() != Node.ELEMENT_NODE) continue;
            if (hasElementChild(hijo)) return null;
            campos.put(hijo.getNodeName(), hijo.getTextContent().trim());
        }
        return campos.size() >= 2 ? campos : null;
    }

    /** Verdadero si el nodo tiene elementos hijos. */
    private static boolean hasElementChild(Node nodo) {
        NodeList hijos = nodo.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            if (hijos.item(i).getNodeType() == Node.ELEMENT_NODE) return true;
        }
        return false;
    }

    /** Parser XML con las protecciones de seguridad activadas. */
    private static DocumentBuilderFactory fabrica() throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            // Se bloquean DOCTYPE y entidades externas para evitar ataques XXE.
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f;
        } catch (ParserConfigurationException e) {
            throw new IOException("No se pudo configurar el parser XML", e);
        }
    }
}
