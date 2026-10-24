package vrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import vrp.engine.model.Coordenada;
import vrp.engine.util.Polyline6;

class Polyline6Test {

    @Test
    void codificarYDecodificarEsReversible() {
        List<Coordenada> puntos = List.of(new Coordenada(-34.603722, -58.381592),
                new Coordenada(-34.6, -58.4), new Coordenada(-31.420082, -64.188776));
        List<Coordenada> vuelta = Polyline6.decodificar(Polyline6.codificar(puntos));
        assertEquals(puntos.size(), vuelta.size());
        for (int i = 0; i < puntos.size(); i++) {
            assertEquals(puntos.get(i).lat(), vuelta.get(i).lat(), 1e-6);
            assertEquals(puntos.get(i).lon(), vuelta.get(i).lon(), 1e-6);
        }
    }
}
