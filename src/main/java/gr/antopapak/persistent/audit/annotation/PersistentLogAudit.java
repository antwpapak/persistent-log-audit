package gr.antopapak.persistent.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A tag annotation to denote that an entity is meant to be
 * audited in the database.<br>
 *
 * By adding this annotation, all mutational action performed to this entity
 * (eg INSERT, DELETE, UPDATE) will be logged. The log information include
 * datetime, user, entity class and id, natural id (if present) and, in case
 * of updates, the affected fields with their values before and after the
 * mutation.
 *
 * By default, all fields of an annotated entity will be audited.<br>
 * In cases where one or more fields should not be logged (eg passwords), they
 * should be annotated with {@link Exclude}, to denote that
 * this field should be ignored.
 *
 * @see gr.antopapak.persistent.audit.model.AffectedField
 * @author Antonis Papakonstantinou
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PersistentLogAudit {


  /**
   * A tag annotation to denote that a field should not be audited.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  @interface Exclude {

  }
}
