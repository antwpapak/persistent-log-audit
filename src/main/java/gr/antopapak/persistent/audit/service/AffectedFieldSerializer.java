package gr.antopapak.persistent.audit.service;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import gr.antopapak.persistent.audit.model.AffectedField;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.jackson.JsonComponent;

@JsonComponent
@RequiredArgsConstructor
public class AffectedFieldSerializer extends JsonSerializer<AffectedField> {

  private final ReflectionCache reflectionCache;

  private static final int MAX_DEPTH = 2;
  private int currentDepth;

  @Override
  public void serialize(AffectedField affectedField, JsonGenerator gen, SerializerProvider serializers) throws IOException {
    String fieldName = affectedField.getFieldName();
    Object before = affectedField.getBefore();
    Object after = affectedField.getAfter();

    currentDepth = 0;

    gen.writeStartObject();
    gen.writeStringField("fieldName", fieldName);
    writeValues("before", before, gen);
    writeValues("after", after, gen);
    gen.writeEndObject();
  }

  private void writeValues(String fieldName, Object value, JsonGenerator gen) throws IOException {
    if(currentDepth > MAX_DEPTH) {
      return;
    }
    
    if(fieldName != null && !fieldName.isBlank()) {
      gen.writeFieldName(fieldName);
    }

    if(isNull(value)) {
      gen.writeNull();
    }
    else if(isBoolean(value)) {
      gen.writeBoolean((Boolean) value);
    }
    else if(isNumber(value)) {
      writeNumber(value, gen);
    }
    else if(isString(value)) {
      gen.writeString(value.toString());
    }
    else if(isCollection(value)) {
      writeArray((Collection<Object>) value, gen);
    }
    else if(isArray(value)) {
      Object[] arr = (Object[]) value;
      writeArray(Arrays.stream(arr).collect(Collectors.toList()), gen);
    }
    else {
      writeObjectLoggableFields(value, gen);
    }
  }

  private void writeArray(Collection<Object> list, JsonGenerator gen) throws IOException {
    currentDepth++;
    gen.writeStartArray();
    for(Object val : list) {
      writeValues(null, val, gen);
    }
    gen.writeEndArray();
    currentDepth = 0;
  }

  private void writeObjectLoggableFields(Object value, JsonGenerator gen) throws IOException {
    currentDepth++;
    gen.writeStartObject();

    reflectionCache.getLoggableFieldsForClass(value.getClass()).forEach(f -> {
      try {
        writeValues(f.getName(), f.get(value), gen);

      } catch (IOException | IllegalAccessException e) {
        e.printStackTrace();
      }
    });

    gen.writeEndObject();
    currentDepth = 0;
  }


  private void writeNumber(Object value, JsonGenerator gen) throws IOException {
    if (value instanceof Integer) {
      gen.writeNumber((Integer) value);
    }
    else if (value instanceof Long) {
      gen.writeNumber((Long) value);
    }
    else if (value instanceof Double) {
      gen.writeNumber((Double) value);
    }
    else if (value instanceof Float) {
      gen.writeNumber((Float) value);
    }
    else if (value instanceof Short) {
      gen.writeNumber((Short) value);
    }
    else if (value instanceof BigInteger) {
      gen.writeNumber((BigInteger) value);
    }
    else if (value instanceof BigDecimal) {
      gen.writeNumber((BigDecimal) value);
    }
  }

  private boolean isBoolean(Object o) {
    return o instanceof Boolean;
  }

  private boolean isNumber(Object o) {
    return o instanceof Number;
  }

  private boolean isString(Object o) {
    return o instanceof String || o instanceof Character;
  }

  private boolean isCollection(Object o) {
    return o instanceof Collection;
  }

  private boolean isArray(Object o) {
    return !isNull(o) && o.getClass().isArray();
  }

  private boolean isNull(Object o) {
    return o == null;
  }

}
