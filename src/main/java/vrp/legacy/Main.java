package vrp.legacy;

import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {

        String currentDir = System.getProperty("user.dir");
        System.out.println("Directorio actual: " + currentDir);

        String filePath = "direcciones.xls";

        // 1) Leer clientes (formato: nombreCliente;direccion).
        List<Cliente> clientes = AddressFileReader.read(filePath);

        if (clientes.size() < 2) {
            throw new IllegalStateException(
                    "Se necesitan al menos 2 direcciones en el archivo");
        }

        // 2) Geocodificar cada cliente con Nominatim.
        //    Rellena lat/lon/display_name. Preserva cliente.getName().
        for (Cliente c : clientes) {
            NominatimClient.geocodificar(c);

            System.out.println("Cliente: " + c.getNombre());
            //Espero 1 seg entre consultas
            try {
                Thread.sleep(1500); // Pausa de 1.5 segundo (1500 milisegundos)
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // 3) Matriz de OSRM.
        String body = OSRM.getMatriz(clientes);
        MatrizOSRM matriz = MatrizOSRM.procesarMatriz(body);

        // 4) Resolver TSP → ruta ordenada de Clientes.
        //    Esta lista es la que después usás para pedir la ruta/polilínea
        //    a Nominatim o al endpoint /route de OSRM.
        List<Cliente> ruta = OrTools.TSP_Solution(matriz, clientes);

        System.out.println("Ruta óptima:");
        int paso = 0;
        for (Cliente c : ruta) {
            System.out.printf(
                    "  %2d. %-20s | %s (%.6f, %.6f)%n",
                    paso++,
                    c.getNombre(),
                    c.getName(),                 // dirección original
                    c.getDireccion().getLat(),   // acceso explícito como Direccion
                    c.getDireccion().getLon()
            );
        }

        // A partir de acá, si quisieras obtener la ruta navegable:
        //   - OSRM /route/v1/driving/{lon,lat;lon,lat;...}?overview=full&geometries=polyline
        //   - o Nominatim para resolver nombres intermedios
        // usando `ruta` como secuencia ordenada de puntos.
    }
}