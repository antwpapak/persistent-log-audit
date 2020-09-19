package gr.antopapak.persistent.audit.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@JsonPropertyOrder({
    "fieldName",
    "before",
    "after"
})
public class AffectedField {
  private final String fieldName;
  private Object before;
  private Object after;
}
