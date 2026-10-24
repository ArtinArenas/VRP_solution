package vrp.engine;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import vrp.engine.geocoding.GeocodingProvider;
import vrp.engine.geocoding.MotivoGeo;
import vrp.engine.geocoding.ResultadoGeo;
import vrp.engine.geocoding.SolicitudGeocodificacion;
import vrp.engine.http.HttpTransporte;
import vrp.engine.model.Coordenada;
import vrp.engine.model.Deposito;
import vrp.engine.model.DireccionPostal;
import vrp.engine.model.Entrega;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.model.Vehiculo;
import vrp.engine.routing.MatrizRuteo;
import vrp.engine.routing.RouteGeometryProvider;
import vrp.engine.routing.RoutingMatrixProvider;
import vrp.engine.solver.ModeloRuteo;
import vrp.engine.solver.ParametrosSolver;
import vrp.engine.solver.RutaNodos;
import vrp.engine.solver.SolucionVrp;
import vrp.engine.solver.VrpSolver;

final class Fakes {

    static final LocalDate FECHA = LocalDate.of(2026, 10, 24);

    private Fakes() {
    }

    static final class HttpFalso implements HttpTransporte {
        final List<String> pedidos = new ArrayList<>();
        final java.util.function.Function<String, String> respuesta;

        HttpFalso(java.util.function.Function<String, String> respuesta) {
            this.respuesta = respuesta;
        }

        @Override
        public String get(URI uri) throws IOException {
            pedidos.add(uri.toString());
            return respuesta.apply(uri.toString());
        }

        @Override
        public String post(URI uri, String cuerpo) throws IOException {
            pedidos.add(uri + " " + cuerpo);
            return respuesta.apply(cuerpo);
        }
    }

    static final class GeoFalso implements GeocodingProvider {
        final String nombre;
        final Map<String, Coordenada> conocidas;
        int llamadas;

        GeoFalso(String nombre, Map<String, Coordenada> conocidas) {
            this.nombre = nombre;
            this.conocidas = conocidas;
        }

        @Override
        public String nombre() {
            return nombre;
        }

        @Override
        public Map<String, ResultadoGeo> geocodificar(List<SolicitudGeocodificacion> solicitudes) {
            llamadas++;
            Map<String, ResultadoGeo> out = new LinkedHashMap<>();
            for (SolicitudGeocodificacion s : solicitudes) {
                Coordenada c = conocidas.get(s.direccion().direccion());
                out.put(s.id(), c != null ? ResultadoGeo.ok(c, s.direccion().texto())
                        : ResultadoGeo.fallo(MotivoGeo.NO_ENCONTRADA, nombre + " no la conoce"));
            }
            return out;
        }
    }

    static final class MatrizFalsa implements RoutingMatrixProvider {
        final String nombre;
        final BiPredicate<Coordenada, Coordenada> sinRuta;
        int llamadas;
        int ultimoTamano;

        MatrizFalsa(String nombre, BiPredicate<Coordenada, Coordenada> sinRuta) {
            this.nombre = nombre;
            this.sinRuta = sinRuta;
        }

        @Override
        public String nombre() {
            return nombre;
        }

        @Override
        public MatrizRuteo calcular(List<Coordenada> puntos) {
            llamadas++;
            ultimoTamano = puntos.size();
            int n = puntos.size();
            double[][] d = new double[n][n];
            long[][] t = new long[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i != j && sinRuta.test(puntos.get(i), puntos.get(j))) {
                        d[i][j] = MatrizRuteo.NO_RUTA;
                        t[i][j] = MatrizRuteo.NO_RUTA;
                    } else {
                        d[i][j] = metros(puntos.get(i), puntos.get(j));
                        t[i][j] = Math.round(d[i][j] / 10.0);
                    }
                }
            }
            return new MatrizRuteo(d, t);
        }
    }

    static final class GeometriaFalsa implements RouteGeometryProvider {
        @Override
        public String nombre() {
            return "geo-falsa";
        }

        @Override
        public List<String> tramos(List<Coordenada> puntos) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < puntos.size() - 1; i++) {
                out.add(vrp.engine.util.Polyline6.codificar(List.of(puntos.get(i), puntos.get(i + 1))));
            }
            return out;
        }
    }

    static final class SolverSecuencial implements VrpSolver {
        @Override
        public SolucionVrp resolver(ModeloRuteo m, MatrizRuteo matriz, ParametrosSolver p) {
            int nv = m.vehiculos().size();
            long[] restante = m.capacidad().clone();
            List<List<Integer>> rutas = new ArrayList<>();
            for (int v = 0; v < nv; v++) rutas.add(new ArrayList<>());
            List<Integer> omitidos = new ArrayList<>();
            for (int i = m.primerNodoEntrega(); i < m.nodos(); i++) {
                boolean asignado = false;
                for (int v = 0; v < nv && !asignado; v++) {
                    if (m.demanda()[i] <= restante[v]) {
                        restante[v] -= m.demanda()[i];
                        rutas.get(v).add(i);
                        asignado = true;
                    }
                }
                if (!asignado) omitidos.add(i);
            }
            List<RutaNodos> salida = new ArrayList<>();
            for (int v = 0; v < nv; v++) salida.add(new RutaNodos(v, rutas.get(v)));
            return new SolucionVrp(salida, omitidos, false);
        }
    }

    static double metros(Coordenada a, Coordenada b) {
        double r = 6371000;
        double f1 = Math.toRadians(a.lat());
        double f2 = Math.toRadians(b.lat());
        double df = f2 - f1;
        double dl = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(df / 2) * Math.sin(df / 2) + Math.cos(f1) * Math.cos(f2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    static Deposito deposito() {
        return new Deposito("D1", "Central", new DireccionPostal("Av. Corrientes 1000", "CABA", "Ciudad Autonoma de Buenos Aires"),
                new Coordenada(-34.60, -58.38));
    }

    static Vehiculo vehiculo(String id, int capacidad) {
        return new Vehiculo(id, id, capacidad, LocalTime.of(8, 0), LocalTime.of(18, 0), "D1", null);
    }

    static Entrega entrega(String id, double lat, double lon, int demanda) {
        return new Entrega(id, id, new DireccionPostal("Calle " + id + " 100", "CABA", "Ciudad Autonoma de Buenos Aires"),
                lat == 0 ? null : new Coordenada(lat, lon), demanda, 5, null, 1);
    }

    static ProblemaVRP problema(int capacidad, Entrega... entregas) {
        return new ProblemaVRP(FECHA, List.of(deposito()), List.of(entregas),
                List.of(vehiculo("V1", capacidad), vehiculo("V2", capacidad)));
    }
}
