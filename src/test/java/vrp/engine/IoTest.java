package vrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vrp.engine.io.CsvProblemReader;
import vrp.engine.io.ExcelProblemReader;
import vrp.engine.io.JsonProblemReader;
import vrp.engine.io.JsonSupport;
import vrp.engine.io.ProblemReaderFactory;
import vrp.engine.io.ResultadoLectura;
import vrp.engine.io.XmlProblemReader;
import vrp.engine.model.ContextoOperativo;
import vrp.engine.model.Entrega;
import vrp.engine.plan.PlanDeRuteo;
import vrp.engine.resultado.PlanGenerado;
import vrp.engine.resultado.ResultadoPlanificacion;

class IoTest {

    private static final ContextoOperativo CONTEXTO = new ContextoOperativo(Fakes.FECHA,
            List.of(Fakes.deposito()), List.of(Fakes.vehiculo("V1", 10)));

    private static final String JSON = """
            {"fecha":"2026-10-24",
             "depositos":[{"id":"D1","nombre":"Central","direccion":{"direccion":"Av. Corrientes 1000","localidad":"CABA","provincia":"Ciudad Autonoma de Buenos Aires"}}],
             "vehiculos":[{"id":"V1","nombre":"Camion","capacidad":10,"horaSalida":"8:00","horaFin":"18:00","depositoSalidaId":"D1"}],
             "entregas":[{"id":"E1","nombre":"Cliente","direccion":{"direccion":"Av. Mitre 123","localidad":"Merlo","provincia":"Buenos Aires"},
                          "demanda":3,"servicioMin":10,"ventana":{"desde":"09:00","hasta":"12:30"},"prioridad":2}]}""";

    @Test
    void jsonLeeProblemaCompleto(@TempDir Path dir) throws IOException {
        Path archivo = Files.writeString(dir.resolve("problema.json"), JSON);
        ResultadoLectura r = new JsonProblemReader().leer(archivo, null);

        assertTrue(r.valido(), r.errores().toString());
        Entrega e = r.problema().entregas().get(0);
        assertEquals(LocalTime.of(12, 30), e.ventana().hasta());
        assertEquals(2, e.prioridad());
        assertEquals("Merlo", e.direccion().localidad());
        assertEquals(LocalTime.of(8, 0), r.problema().vehiculos().get(0).horaSalida());
        assertNull(r.problema().depositos().get(0).coordenada());
    }

    @Test
    void jsonInvalidoDevuelveErroresSinExcepcion(@TempDir Path dir) throws IOException {
        Path archivo = Files.writeString(dir.resolve("roto.json"), "{\"entregas\": [ {");
        ResultadoLectura r = new JsonProblemReader().leer(archivo, null);
        assertNull(r.problema());
        assertFalse(r.errores().isEmpty());
    }

    @Test
    void jsonSinFlotaUsaElContextoOperativo() {
        String soloEntregas = "{\"entregas\":[{\"id\":\"E1\",\"direccion\":{\"direccion\":\"Mitre 1\",\"provincia\":\"Buenos Aires\"}}]}";
        ResultadoLectura r = new JsonProblemReader().leerTexto(soloEntregas, CONTEXTO);
        assertTrue(r.valido(), r.errores().toString());
        assertEquals(1, r.problema().vehiculos().size());
        assertEquals(Fakes.FECHA, r.problema().fecha());
    }

    @Test
    void csvLeeCamposSeparadosYReportaFilasInvalidasPorSeparado(@TempDir Path dir) throws IOException {
        String csv = """
                id;nombre;direccion;localidad;provincia;demanda;servicio_min;ventana_desde;ventana_hasta;prioridad
                E1;Juan;Av. Mitre 123;Merlo;Buenos Aires;2;10;9:00;12:00;3
                ;Ana;Calle 9 45;Moron;Buenos Aires;1;;;;
                E3;Luis;;Moron;Buenos Aires;1;;;;
                E4;Eva;Calle 1;Moron;Buenos Aires;abc;;;;
                """;
        Path archivo = Files.writeString(dir.resolve("clientes.csv"), csv, StandardCharsets.UTF_8);

        ResultadoLectura r = new CsvProblemReader().leer(archivo, CONTEXTO);

        assertEquals(3, r.problema().entregas().size());
        Entrega e1 = r.problema().entregas().get(0);
        assertEquals("Av. Mitre 123", e1.direccion().direccion());
        assertEquals("Merlo", e1.direccion().localidad());
        assertEquals(LocalTime.of(9, 0), e1.ventana().desde());
        assertEquals(3, e1.prioridad());
        assertEquals("E2", r.problema().entregas().get(1).id());
        assertEquals(2, r.errores().size());
        assertTrue(r.errores().stream().anyMatch(x -> x.referencia().equals("E3")));
        assertTrue(r.errores().stream().anyMatch(x -> x.referencia().equals("E4")));
    }

    @Test
    void csvSinContextoOperativoNoFallaConExcepcion(@TempDir Path dir) throws IOException {
        Path archivo = Files.writeString(dir.resolve("c.csv"), "nombre;direccion\nA;B\n");
        ResultadoLectura r = new CsvProblemReader().leer(archivo, null);
        assertNull(r.problema());
        assertEquals(1, r.errores().size());
    }

    @Test
    void xmlLeeEntregasComoRegistros(@TempDir Path dir) throws IOException {
        String xml = """
                <entregas>
                  <entrega><nombre>Juan</nombre><direccion>Av. Mitre 123</direccion><localidad>Merlo</localidad>
                    <provincia>Buenos Aires</provincia><demanda>2</demanda></entrega>
                  <entrega><nombre>Ana</nombre><direccion>Calle 9 45</direccion><localidad>Moron</localidad>
                    <provincia>Buenos Aires</provincia></entrega>
                </entregas>""";
        Path archivo = Files.writeString(dir.resolve("clientes.xml"), xml);
        ResultadoLectura r = new XmlProblemReader().leer(archivo, CONTEXTO);
        assertTrue(r.valido(), r.errores().toString());
        assertEquals(2, r.problema().entregas().size());
        assertEquals(2, r.problema().entregas().get(0).demanda());
        assertEquals("Moron", r.problema().entregas().get(1).direccion().localidad());
    }

    @Test
    void xmlConDoctypeEsRechazado(@TempDir Path dir) throws IOException {
        String xml = "<!DOCTYPE x [<!ENTITY a \"b\">]><entregas><entrega><nombre>&a;</nombre><direccion>x</direccion></entrega></entregas>";
        Path archivo = Files.writeString(dir.resolve("malo.xml"), xml);
        assertThrows(IOException.class, () -> new XmlProblemReader().leer(archivo, CONTEXTO));
    }

    @Test
    void excelLeeEncabezadosPorNombre(@TempDir Path dir) throws IOException {
        Path archivo = dir.resolve("clientes.xls");
        try (HSSFWorkbook libro = new HSSFWorkbook(); OutputStream out = Files.newOutputStream(archivo)) {
            Sheet hoja = libro.createSheet("clientes");
            Row h = hoja.createRow(0);
            String[] cols = {"Nombre", "Dirección", "Localidad", "Provincia", "Demanda"};
            for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);
            Row f = hoja.createRow(1);
            f.createCell(0).setCellValue("Juan");
            f.createCell(1).setCellValue("Av. Mitre 123");
            f.createCell(2).setCellValue("Merlo");
            f.createCell(3).setCellValue("Buenos Aires");
            f.createCell(4).setCellValue(4);
            libro.write(out);
        }
        ResultadoLectura r = new ExcelProblemReader().leer(archivo, CONTEXTO);
        assertTrue(r.valido(), r.errores().toString());
        assertEquals(4, r.problema().entregas().get(0).demanda());
        assertEquals("Av. Mitre 123", r.problema().entregas().get(0).direccion().direccion());
    }

    @Test
    void fabricaEligePorExtensionYRechazaDesconocidas() {
        assertInstanceOf(JsonProblemReader.class, ProblemReaderFactory.para(Path.of("a.JSON")));
        assertInstanceOf(CsvProblemReader.class, ProblemReaderFactory.para(Path.of("a.csv")));
        assertInstanceOf(ExcelProblemReader.class, ProblemReaderFactory.para(Path.of("a.xlsx")));
        assertInstanceOf(XmlProblemReader.class, ProblemReaderFactory.para(Path.of("a.xml")));
        assertThrows(IllegalArgumentException.class, () -> ProblemReaderFactory.para(Path.of("a.txt")));
    }

    @Test
    void planSeSerializaConEstadosEnMinusculaYSePuedeLeerDeVuelta() throws IOException {
        var p = Fakes.problema(10, Fakes.entrega("E1", PlanificadorTest.C1.lat(), PlanificadorTest.C1.lon(), 1));
        PlanDeRuteo plan = ((PlanGenerado) PlanificadorTest.planificador().planificar(p)).plan();

        String json = JsonSupport.mapper().writeValueAsString(plan);
        JsonNode raiz = JsonSupport.mapper().readTree(json);
        JsonNode parada = raiz.path("rutas").get(0).path("paradas").get(0);

        assertEquals("pendiente", parada.path("estado").asText());
        assertEquals("valido", raiz.path("estado").asText());
        assertTrue(parada.path("eta").asText().startsWith("2026-10-24T08:"));
        assertEquals(plan, JsonSupport.mapper().readValue(json, PlanDeRuteo.class));

        String resultado = JsonSupport.mapper().writeValueAsString((ResultadoPlanificacion) new PlanGenerado(plan));
        assertEquals("plan_generado", JsonSupport.mapper().readTree(resultado).path("resultado").asText());
    }
}
