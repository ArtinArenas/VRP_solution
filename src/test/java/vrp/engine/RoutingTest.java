package vrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vrp.engine.http.HttpTransporte;
import vrp.engine.model.Coordenada;
import vrp.engine.routing.BloqueMatriz;
import vrp.engine.routing.BloquesMatrixProvider;
import vrp.engine.routing.MatrixChain;
import vrp.engine.routing.MatrizRuteo;
import vrp.engine.routing.OsrmClient;
import vrp.engine.routing.RoutingException;
import vrp.engine.routing.RoutingMatrixProvider;
import vrp.engine.routing.ValhallaClient;

class RoutingTest {

    private static List<Coordenada> puntos(int n) {
        List<Coordenada> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new Coordenada(-34.0 - i * 0.01, -58.0));
        return l;
    }

    @Test
    void bloquesArmanLaMatrizCompletaConTamanoMenorAlTotal() {
        List<int[]> bloques = new ArrayList<>();
        BloquesMatrixProvider p = new BloquesMatrixProvider(2) {
            @Override
            public String nombre() {
                return "bloques";
            }

            @Override
            protected BloqueMatriz pedirBloque(List<Coordenada> o, List<Coordenada> d) {
                bloques.add(new int[]{o.size(), d.size()});
                double[][] dist = new double[o.size()][d.size()];
                long[][] t = new long[o.size()][d.size()];
                for (int i = 0; i < o.size(); i++) {
                    for (int j = 0; j < d.size(); j++) {
                        dist[i][j] = Math.abs(o.get(i).lat() - d.get(j).lat()) * 1000;
                        t[i][j] = Math.round(dist[i][j]);
                    }
                }
                return new BloqueMatriz(dist, t);
            }
        };

        List<Coordenada> pts = puntos(5);
        MatrizRuteo m = p.calcular(pts);

        assertEquals(9, bloques.size());
        assertTrue(m.sinHuecos());
        assertEquals(Math.abs(pts.get(0).lat() - pts.get(4).lat()) * 1000, m.distanciasM()[0][4], 1e-9);
        assertEquals(0, m.tiemposSeg()[3][3]);
    }

    @Test
    void cadenaCompletaHuecosConElSecundario() {
        RoutingMatrixProvider primario = new Fakes.MatrizFalsa("valhalla", (a, b) -> a.lat() > b.lat() && b.lat() < -34.015);
        RoutingMatrixProvider secundario = new Fakes.MatrizFalsa("osrm", (a, b) -> false);
        MatrizRuteo m = new MatrixChain(List.of(primario, secundario)).calcular(puntos(4));
        assertTrue(m.sinHuecos());
    }

    @Test
    void cadenaDevuelveHuecosSiAmbosFallanYLanzaSiNingunoResponde() {
        RoutingMatrixProvider siempreHueco = new Fakes.MatrizFalsa("a", (x, y) -> x.lat() == -34.0 && y.lat() == -34.02);
        MatrizRuteo m = new MatrixChain(List.of(siempreHueco, siempreHueco)).calcular(puntos(3));
        assertFalse(m.sinHuecos());

        RoutingMatrixProvider caido = new RoutingMatrixProvider() {
            @Override
            public String nombre() {
                return "caido";
            }

            @Override
            public MatrizRuteo calcular(List<Coordenada> p) {
                throw new RoutingException("caido: sin conexion");
            }
        };
        assertThrows(RoutingException.class, () -> new MatrixChain(List.of(caido)).calcular(puntos(2)));
    }

    @Test
    void valhallaConvierteKmAMetrosYNullASinRuta() {
        String json = """
                {"sources_to_targets":[
                  [{"distance":0.0,"time":0},{"distance":1.5,"time":120}],
                  [{"distance":null,"time":null},{"distance":0.0,"time":0}]
                ]}""";
        Fakes.HttpFalso http = new Fakes.HttpFalso(c -> json);
        MatrizRuteo m = new ValhallaClient(http, "http://valhalla:8002", "auto", 50).calcular(puntos(2));
        assertEquals(1500.0, m.distanciasM()[0][1], 1e-9);
        assertEquals(120, m.tiemposSeg()[0][1]);
        assertFalse(m.hayRuta(1, 0));
    }

    @Test
    void osrmInterpretaNullsYArmaUrlCuadrada() {
        String json = "{\"code\":\"Ok\",\"distances\":[[0,900],[null,0]],\"durations\":[[0,70.4],[null,0]]}";
        Fakes.HttpFalso http = new Fakes.HttpFalso(c -> json);
        MatrizRuteo m = new OsrmClient(http, "http://osrm:5000", 50).calcular(puntos(2));
        assertEquals(70, m.tiemposSeg()[0][1]);
        assertFalse(m.hayRuta(1, 0));
        assertTrue(http.pedidos.get(0).contains("annotations=distance,duration"));
        assertFalse(http.pedidos.get(0).contains("sources="));
    }

    @Test
    void osrmConErrorDeCodigoLanzaRoutingException() {
        HttpTransporte http = new Fakes.HttpFalso(c -> "{\"code\":\"InvalidQuery\",\"message\":\"mal\"}");
        assertThrows(RoutingException.class, () -> new OsrmClient(http, "http://osrm", 50).calcular(puntos(2)));
    }
}
