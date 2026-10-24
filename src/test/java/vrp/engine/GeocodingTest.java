package vrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vrp.engine.geocoding.GeocodingChain;
import vrp.engine.geocoding.GeorefProvider;
import vrp.engine.geocoding.MotivoGeo;
import vrp.engine.geocoding.NominatimLocalProvider;
import vrp.engine.geocoding.ResolucionGeo;
import vrp.engine.geocoding.ResultadoGeo;
import vrp.engine.geocoding.SolicitudGeocodificacion;
import vrp.engine.model.Coordenada;
import vrp.engine.model.DireccionPostal;

class GeocodingTest {

    private static SolicitudGeocodificacion solicitud(String id, String calle) {
        return new SolicitudGeocodificacion(id, new DireccionPostal(calle, "Merlo", "Buenos Aires"));
    }

    @Test
    void georefEnviaLoteConProvinciaYLocalidadYParseaResultados() {
        String respuesta = """
                {"resultados":[
                  {"cantidad":1,"direcciones":[{"nomenclatura":"SANTA FE 3100","ubicacion":{"lat":-34.59,"lon":-58.41}}]},
                  {"cantidad":0,"direcciones":[]},
                  {"cantidad":1,"direcciones":[{"ubicacion":{"lat":null,"lon":null}}]}
                ]}""";
        Fakes.HttpFalso http = new Fakes.HttpFalso(c -> respuesta);
        GeorefProvider georef = new GeorefProvider(http, "https://apis.datos.gob.ar/georef/api/v2.0/");

        Map<String, ResultadoGeo> r = georef.geocodificar(List.of(
                solicitud("a", "Santa Fe 3100"), solicitud("b", "Inexistente 1"), solicitud("c", "Sin ubicacion 5")));

        assertEquals(1, http.pedidos.size());
        assertTrue(http.pedidos.get(0).startsWith("https://apis.datos.gob.ar/georef/api/v2.0/direcciones "));
        assertTrue(http.pedidos.get(0).contains("\"provincia\":\"Buenos Aires\""));
        assertTrue(http.pedidos.get(0).contains("\"localidad\":\"Merlo\""));
        assertEquals(new Coordenada(-34.59, -58.41), r.get("a").coordenada());
        assertEquals(MotivoGeo.NO_ENCONTRADA, r.get("b").motivo());
        assertEquals(MotivoGeo.SIN_COORDENADAS, r.get("c").motivo());
    }

    @Test
    void georefMarcaTodoComoNoDisponibleSiFallaElLote() {
        Fakes.HttpFalso http = new Fakes.HttpFalso(c -> {
            throw new RuntimeException("caido");
        });
        Map<String, ResultadoGeo> r = new GeorefProvider(http, "http://x")
                .geocodificar(List.of(solicitud("a", "Calle 1"), solicitud("b", "Calle 2")));
        assertEquals(MotivoGeo.PROVEEDOR_NO_DISPONIBLE, r.get("a").motivo());
        assertEquals(MotivoGeo.PROVEEDOR_NO_DISPONIBLE, r.get("b").motivo());
    }

    @Test
    void nominatimParseaLatLonComoTexto() {
        Fakes.HttpFalso http = new Fakes.HttpFalso(c -> "[{\"lat\":\"-34.7\",\"lon\":\"-58.7\",\"display_name\":\"Merlo\"}]");
        Map<String, ResultadoGeo> r = new NominatimLocalProvider(http, "http://localhost:8080", "a@b.c", 0)
                .geocodificar(List.of(solicitud("a", "Calle 1")));
        assertEquals(new Coordenada(-34.7, -58.7), r.get("a").coordenada());
        assertTrue(http.pedidos.get(0).contains("countrycodes=ar"));
        assertTrue(http.pedidos.get(0).contains("email=a%40b.c"));
    }

    @Test
    void cadenaUsaFallbackSoloParaLasPendientes() {
        Fakes.GeoFalso primero = new Fakes.GeoFalso("georef", Map.of("Calle 1", new Coordenada(-34.6, -58.4)));
        Fakes.GeoFalso segundo = new Fakes.GeoFalso("nominatim", Map.of("Calle 2", new Coordenada(-34.7, -58.5)));
        Map<String, ResolucionGeo> r = new GeocodingChain(List.of(primero, segundo))
                .resolver(List.of(solicitud("a", "Calle 1"), solicitud("b", "Calle 2"), solicitud("c", "Calle 3")));

        assertEquals("georef", r.get("a").proveedorExitoso());
        assertEquals("nominatim", r.get("b").proveedorExitoso());
        assertEquals(List.of("georef", "nominatim"), r.get("b").proveedoresIntentados());
        assertFalse(r.get("c").resultado().exito());
        assertEquals(MotivoGeo.NO_ENCONTRADA, r.get("c").resultado().motivo());
        assertEquals(List.of("georef", "nominatim"), r.get("c").proveedoresIntentados());
    }

    @Test
    void cadenaRechazaCoordenadasFueraDeArgentina() {
        Fakes.GeoFalso afuera = new Fakes.GeoFalso("georef", Map.of("Calle 1", new Coordenada(40.4, -3.7)));
        Map<String, ResolucionGeo> r = new GeocodingChain(List.of(afuera)).resolver(List.of(solicitud("a", "Calle 1")));
        assertEquals(MotivoGeo.FUERA_DE_ARGENTINA, r.get("a").resultado().motivo());
    }
}
