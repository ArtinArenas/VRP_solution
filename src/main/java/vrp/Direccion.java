package vrp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Direccion {
    // Atributos con los mismos nombres del JSON
    private String name;
    private double lat;
    private double lon;
    private String display_name;

    // Constructor vacío para Jackson
    public Direccion() {}

    public Direccion(String name, double lat, double lon, String display_name) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.display_name = display_name;
    }

    // Getters y Setters actualizados
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public String getDisplay_name() { return display_name; }
    public void setDisplay_name(String display_name) { this.display_name = display_name; }
}
