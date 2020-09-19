package gr.antopapak.persistent.audit.domain;

import com.vladmihalcea.hibernate.type.json.JsonStringType;
import gr.antopapak.persistent.audit.model.AuditedAction;
import java.io.Serializable;
import java.time.Instant;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.springframework.format.annotation.DateTimeFormat;

@Entity
@Table(name = "audit_logs")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@TypeDef(
    name = "json",
    typeClass = JsonStringType.class
)
public class AuditLog implements Serializable {

  protected static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Basic
  @Column(name = "auditor", nullable = false)
  private String auditor;

  @Basic
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Column(name = "datetime", nullable = false)
  private Instant dateTime;

  @Basic
  @Column(name = "entity_class_name", nullable = false)
  private String entityClassName;

  @Basic
  @Column(name = "entity_id", nullable = false)
  private String entityId;

  @Basic
  @Column(name = "entity_natural_id")
  private String entityNaturalId;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false)
  private AuditedAction action;

  @Basic
  @Column(name = "affected_fields", columnDefinition = "json")
  @Type(type = "json")
  private String affectedFields;

}
