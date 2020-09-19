package gr.antopapak.persistent.audit.service;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import gr.antopapak.persistent.audit.annotation.PersistentLogAudit;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.hibernate.annotations.NaturalId;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.stereotype.Component;


/**
 * A class that stores fields and metadata about them, grouped by classes.
 */
@Component
class ReflectionCache {

  private final static Set<Class<? extends Annotation>> excludedAnnotations = Set.of(
      Id.class,
      javax.persistence.Id.class,
      CreatedBy.class,
      LastModifiedBy.class,
      CreatedDate.class,
      LastModifiedDate.class,
      PersistentLogAudit.Exclude.class
  );

  private final static Map<String, List<Field>> loggableFields = new HashMap<>();
  private final static Map<String, Field> naturalIdFields = new HashMap<>();
  private final static Map<String, Field> idFields = new HashMap<>();
  private final static Map<String, Method> findByIdMethods = new HashMap<>();


  /**
   * Returns a list of fields that are eligible from auditing.<br>
   * In other words, returns all the fields that are not annotated with {@link PersistentLogAudit.Exclude}
   * or any of the annotations defined in {@link #excludedAnnotations}
   * <p>
   * If null is passed as a class param, null is returned.
   * @param c the class to get the fields of
   * @return all the loggable fields or null is c is null
   */
  List<Field> getLoggableFieldsForClass(Class<?> c) {
    if(isNull(c)) {
      return null;
    }

    List<Field> cachedFields = loggableFields.get(getKey(c));

    if(isNull(cachedFields)) {
      cachedFields = getAllFieldsToLog(c);
      loggableFields.put(getKey(c), cachedFields);
    }

    return cachedFields;
  }

  /**
   * Returns the field annotated with @Id.
   *
   * @param c the class
   * @return the id field
   */
  Field getIdForClass(Class<?> c) {
    String key = getKey(c);
    if(!idFields.containsKey(key)) {
      Field id = getEntityIdField(c);
      idFields.put(key, id);
    }

    return idFields.get(key);
  }

  /**
   * Returns a field that is annotated with @NaturalId if one exists.
   * @param c the class
   * @return the natualId field or null
   */
  Field getNaturalIdForClass(Class<?> c) {
    if(!naturalIdFields.containsKey(getKey(c))) {
      //this is called just for its side effects
      getLoggableFieldsForClass(c);

      //if there is still no NaturalId, keep the key with a null value
      //so that we know we've already searched
      if(!naturalIdFields.containsKey(getKey(c))) {
        naturalIdFields.put(getKey(c), null);
      }
    }
    return naturalIdFields.get(getKey(c));
  }

  /**
   * Returns the findByIdMethod of a class if present
   * @param c the class
   * @return the findById method or null
   */
  Method getFindByIdMethodForClass(Class<?> c) {
    if(c == null) {
      return null;
    }

    Method findById = findByIdMethods.get(getKey(c));

    if(!findByIdMethods.containsKey(getKey(c))) {

      Class<?> clazz = c;
      while(findById == null && clazz != null) {
        findById = Arrays.stream(clazz.getMethods()).filter(method -> method.getName().equals("findById")).findFirst().orElse(null);
        clazz = clazz.getSuperclass();
      }

      if(findById != null) {
        findById.setAccessible(true);
      }
      findByIdMethods.put(getKey(c), findById);
    }

    return findById;
  }

  private String getKey(Class<?> c) {
    if(c == null) {
      return "";
    }
    return c.getCanonicalName();
  }

  /**
   * Returns all the loggable fields of a class
   * @param type the class
   * @return the loggable fields of a class
   */
  private List<Field> getAllFieldsToLog(Class<?> type) {
    return getAllFields(new ArrayList<>(), type, this::shouldLogField);
  }

  /**
   * Returns all fields of a class, filtered with a given predicate.
   * IF the predicate is null, all the fields are returned.
   *
   * @param fields the list to add the fields into. Cannot be null
   * @param type the class
   * @param filter the predicate used on returned fields
   *
   * @return the fields of a class, filtered with a given predicate
   */
  private List<Field> getAllFields(List<Field> fields, Class<?> type, Predicate<Field> filter) {
    requireNonNull(fields);

    if(isNull(filter)) {
      filter = f -> true;
    }
    List<Field> declaredLoggableFields = Arrays
        .stream(type.getDeclaredFields())
        .filter(filter)
        .peek(f -> {
          f.setAccessible(true);
          if (isNaturalId(f)) {
            naturalIdFields.put(getKey(type), f);
          }
        })
        .collect(Collectors.toList());

    fields.addAll(declaredLoggableFields);

    if (type.getSuperclass() != null) {
      getAllFields(fields, type.getSuperclass(), filter);
    }

    return fields;
  }


  private boolean shouldLogField(Field field) {
    return field != null
        && Arrays.stream(field.getDeclaredAnnotations()).map(Annotation::annotationType).noneMatch(excludedAnnotations::contains);
  }

  private boolean isNaturalId(Field field) {
    return field != null
        && field.isAnnotationPresent(NaturalId.class);
  }

  /**
   * Returns the field annotated with @Id.
   *
   * @param c the class
   * @return the id field
   */
  private Field getEntityIdField(Class<?> c) {
    Predicate<Field> isId = f ->
        f.isAnnotationPresent(Id.class) || f.isAnnotationPresent(org.springframework.data.annotation.Id.class);
    List<Field> fields = getAllFields(new ArrayList<>(), c, isId);

    if(fields.isEmpty()) {
      return null;
    }
    if(fields.size() > 1) {
      throw new IllegalStateException("Multiple ids found: " + c.getName());
    }

    return fields.get(0);
  }
}
