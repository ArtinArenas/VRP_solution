package vrp.engine.io;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import vrp.engine.util.Tiempos;

public final class JsonSupport {

    private static final ObjectMapper MAPPER = crear();

    private JsonSupport() {
    }

    /** Mapper compartido para leer y escribir todos los JSON del motor. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Configura Jackson: horas y fechas como texto, enums en minúscula y nulos omitidos. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ObjectMapper crear() {
        SimpleModule modulo = new SimpleModule("vrp-engine");
        modulo.addSerializer(LocalTime.class, ToStringSerializer.instance);
        modulo.addSerializer(LocalDate.class, ToStringSerializer.instance);
        modulo.addSerializer(LocalDateTime.class, ToStringSerializer.instance);
        // Los enums viajan en minúscula (por ejemplo "en_camino").
        modulo.addSerializer(Enum.class, new StdSerializer<Enum>(Enum.class) {
            /** Escribe el enum en minúscula. */
            @Override
            public void serialize(Enum valor, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(valor.name().toLowerCase(Locale.ROOT));
            }
        });
        modulo.addDeserializer(LocalTime.class, new StdDeserializer<LocalTime>(LocalTime.class) {
            /** Lee horas como texto; acepta "8:00" y "08:00". */
            @Override
            public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return Tiempos.parseHora(p.getValueAsString());
            }
        });
        modulo.addDeserializer(LocalDate.class, new StdDeserializer<LocalDate>(LocalDate.class) {
            /** Lee fechas ISO como texto. */
            @Override
            public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String s = p.getValueAsString();
                return s == null || s.isBlank() ? null : LocalDate.parse(s.trim());
            }
        });
        modulo.addDeserializer(LocalDateTime.class, new StdDeserializer<LocalDateTime>(LocalDateTime.class) {
            /** Lee fecha y hora ISO como texto. */
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String s = p.getValueAsString();
                return s == null || s.isBlank() ? null : LocalDateTime.parse(s.trim());
            }
        });

        return new ObjectMapper()
                .registerModule(modulo)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
