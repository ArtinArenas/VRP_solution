package vrp.engine.io;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import vrp.engine.model.ContextoOperativo;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.resultado.ErrorLectura;

public final class JsonProblemReader implements ProblemReader {

    /** Reconoce archivos .json. */
    @Override
    public boolean soporta(Path archivo) {
        return archivo.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    /** Lee un JSON completo; un JSON mal formado se informa como error, no como excepción. */
    @Override
    public ResultadoLectura leer(Path archivo, ContextoOperativo contexto) throws IOException {
        ProblemaVRP leido;
        try {
            leido = JsonSupport.mapper().readValue(archivo.toFile(), ProblemaVRP.class);
        } catch (JsonProcessingException e) {
            return new ResultadoLectura(null,
                    List.of(new ErrorLectura(archivo.getFileName().toString(), "JSON invalido: " + e.getOriginalMessage())));
        }
        return completar(leido, contexto);
    }

    /** Igual que leer, pero desde un texto JSON (útil para una API). */
    public ResultadoLectura leerTexto(String json, ContextoOperativo contexto) {
        ProblemaVRP leido;
        try {
            leido = JsonSupport.mapper().readValue(json, ProblemaVRP.class);
        } catch (JsonProcessingException e) {
            return new ResultadoLectura(null, List.of(new ErrorLectura("json", "JSON invalido: " + e.getOriginalMessage())));
        }
        return completar(leido, contexto);
    }

    /** Completa lo que falte con el contexto operativo y valida el resultado. */
    private static ResultadoLectura completar(ProblemaVRP leido, ContextoOperativo contexto) {
        ProblemaVRP problema = leido;
        if (contexto != null) {
            // Lo que el archivo no trae se completa con el contexto operativo.
            problema = new ProblemaVRP(
                    leido.fecha() != null ? leido.fecha() : contexto.fecha(),
                    leido.depositos().isEmpty() ? contexto.depositos() : leido.depositos(),
                    leido.entregas(),
                    leido.vehiculos().isEmpty() ? contexto.vehiculos() : leido.vehiculos());
        }
        return new ResultadoLectura(problema, new ArrayList<>(ValidadorProblema.validar(problema)));
    }
}
