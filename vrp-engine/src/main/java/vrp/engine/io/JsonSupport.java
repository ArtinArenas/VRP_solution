package vrp.engine.io;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import vrp.engine.util.Tiempos;

/**
 * Configuración centralizada de Jackson para el motor VRP.
 * <p>
 * Características:
 * - JavaTimeModule para serialización ISO-8601 de LocalDate, LocalTime, LocalDateTime
 * - PropertyNamingStrategy LOWER_CAMEL_CASE (consistente con records Java)
 * - Enums serializados en minúscula (ej: "en_camino")
 * - Deserialización tolerante a campos desconocidos (FAIL_ON_UNKNOWN_PROPERTIES = false)
 * - Soporte para tipos polimórficos (sealed interfaces) via @JsonTypeInfo
 * - Records de Java 25 soportados nativamente (sin constructores mutables)
 * - Campos nulos omitidos en serialización
 * - Detección automática de campos en records (incluye getters y fields)
 */
public final class JsonSupport {

    private static final ObjectMapper MAPPER = crear();

    private JsonSupport() {
    }

    /** Mapper compartido para leer y escribir todos los JSON del motor. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Configura Jackson con las opciones estándar del motor VRP. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ObjectMapper crear() {
        // Módulo para tipos de fecha/hora Java 8+ (ISO-8601)
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // Módulo personalizado para enums en minúscula y parsing flexible de horas
        SimpleModule modulo = new SimpleModule("vrp-engine");
        // Los enums viajan en minúscula (por ejemplo "en_camino")
        modulo.addSerializer(Enum.class, new StdSerializer<Enum>(Enum.class) {
            @Override
            public void serialize(Enum valor, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(valor.name().toLowerCase(Locale.ROOT));
            }
        });
        // Parsing flexible de LocalTime: acepta "8:00", "08:00", "08:00:00"
        modulo.addDeserializer(LocalTime.class, new StdDeserializer<LocalTime>(LocalTime.class) {
            @Override
            public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String s = p.getValueAsString();
                return s == null || s.isBlank() ? null : Tiempos.parseHora(s.trim());
            }
        });

        return new ObjectMapper()
                // Detectar campos en records (fields y getters) sin requerir anotaciones
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.ANY)
                .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.ANY)
                // Estrategia de nombrado consistente: lowerCamelCase (estándar Java records)
                .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.LOWER_CAMEL_CASE)
                // Módulos
                .registerModule(javaTimeModule)
                .registerModule(modulo)
                // Configuración de serialización
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false) // ISO-8601 string
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                // Configuración de deserialización
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                // Permitir deserialización en records sin constructor por defecto
                .configure(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS, false);
    }
}
