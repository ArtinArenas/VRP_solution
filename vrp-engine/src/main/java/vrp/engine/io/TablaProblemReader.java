package vrp.engine.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import vrp.engine.model.ContextoOperativo;
import vrp.engine.model.Entrega;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.resultado.ErrorLectura;

public abstract class TablaProblemReader implements ProblemReader {

    /** Devuelve cada fila como un mapa de encabezado a valor. */
    protected abstract List<Map<String, String>> leerFilas(Path archivo) throws IOException;

    /** Convierte las filas en entregas y las valida junto con el contexto operativo. */
    @Override
    public ResultadoLectura leer(Path archivo, ContextoOperativo contexto) throws IOException {
        // Los formatos tabulares no traen flota ni depósitos: son obligatorios por argumento.
        if (contexto == null) {
            return new ResultadoLectura(null, List.of(new ErrorLectura(archivo.getFileName().toString(),
                    "Los formatos tabulares requieren el contexto operativo (fecha, depositos y vehiculos)")));
        }

        List<Map<String, String>> filas = leerFilas(archivo);
        List<Entrega> entregas = new ArrayList<>();
        List<ErrorLectura> errores = new ArrayList<>();
        int numero = 0;
        for (Map<String, String> fila : filas) {
            numero++;
            FilaEntrega.Resultado r = FilaEntrega.mapear(fila, numero);
            if (r.error() != null) errores.add(r.error());
            else entregas.add(r.entrega());
        }

        ProblemaVRP problema = new ProblemaVRP(contexto.fecha(), contexto.depositos(), entregas, contexto.vehiculos());
        // Los errores de filas y de estructura se informan juntos, sin cortar en el primero.
        errores.addAll(ValidadorProblema.validar(problema));
        return new ResultadoLectura(problema, errores);
    }
}
