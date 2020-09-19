package gr.antopapak.persistent.audit.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import gr.antopapak.persistent.audit.model.AffectedField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A helper class that keeps the before and after state of the fields of a class
 */
class FieldMap {

  private Map<String, AffectedField> map = new HashMap<>();

  void put(String name, Object before, Object after) {
    AffectedField entry = new AffectedField(
        Objects.requireNonNull(name),
        before != null ? before : "NULL",
        after != null ? after : "NULL"
    );

    map.put(name, entry);
  }

  void putBefore(String name, Object before) {
    AffectedField entry = map.get(name);
    if(entry == null) {
      entry = new AffectedField(Objects.requireNonNull(name));
    }
    entry.setBefore(before != null ? before : "NULL");

    map.put(name, entry);
  }

  void putAfter(String name, Object after) {
    AffectedField entry = map.get(name);
    if(entry == null) {
      entry = new AffectedField(Objects.requireNonNull(name));
    }
    entry.setAfter(after != null ? after : "NULL");

    map.put(name, entry);
  }

  Object getBefore(String name) {
    if(!map.containsKey(name)) {
      throw new NoSuchElementException(name);
    }
    return map.get(name).getBefore();
  }

  Object getAfter(String name) {
    if(!map.containsKey(name)) {
      throw new NoSuchElementException(name);
    }
    return map.get(name).getAfter();
  }

  int size() {
    return map.size();
  }

  List<AffectedField> getAllEntries() {
    return List.copyOf(map.values());
  }

  List<AffectedField> getAllAffectedEntries() {
    return getAllEntries()
        .stream()
        .filter(this::isFieldAffected)
        .collect(Collectors.toList());
  }

  boolean isFieldAffected(String name) {
    if(!map.containsKey(name)) {
      return false;
    }

    AffectedField entry = map.get(name);
    Object before = entry.getBefore();
    Object after = entry.getAfter();
    return (isNull(before) && nonNull(after)) || !before.equals(after);
  }

  private boolean isFieldAffected(AffectedField entry) {
    return isFieldAffected(Objects.requireNonNull(entry).getFieldName());
  }
}
