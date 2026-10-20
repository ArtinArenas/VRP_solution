package vrp;
import java.io.IOException;
import java.util.List;
import static vrp.NominatimClient.getDireccion;


public class Main {
    static void main() throws IOException, InterruptedException {

        String direccion = "Marambio 2873, Ituzaingo";
        Direccion d;

        d = getDireccion(direccion);
        IO.println(d.getDisplay_name());
        IO.println(d.getLat());
        IO.println(d.getLon());
    }
}
