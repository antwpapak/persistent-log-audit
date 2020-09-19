package gr.antopapak.persistent.audit.service;


import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.antopapak.persistent.audit.annotation.PersistentLogAudit;
import gr.antopapak.persistent.audit.domain.AuditLog;
import gr.antopapak.persistent.audit.model.AffectedField;
import gr.antopapak.persistent.audit.model.AuditedAction;
import gr.antopapak.persistent.audit.repository.AuditLogRepository;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class AuditLogService {

  private final AuditorAware<String> auditorAwareBean;
  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;
  private final ReflectionCache reflectionCache;



  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void createNonDeletingAudit(Object entityBefore, Object entityAfter)
      throws IllegalAccessException, JsonProcessingException {

    Object idAfter = getEntityId(entityAfter);

    if(isNull(entityBefore) && nonNull(idAfter)) { //is new entity
      createInsertAuditLog(entityAfter, idAfter);
    }
    else {
      FieldMap fieldMap = new FieldMap();
      putFieldsBefore(fieldMap, entityBefore);
      putFieldsAfter(fieldMap, entityAfter);

      if(fieldMap.size() == 1 && fieldMap.isFieldAffected("deleted")) {
        Boolean isNowDeleted = (Boolean) fieldMap.getAfter("deleted");
        if(isNowDeleted) {
          createSoftDeleteAuditLog(entityAfter, idAfter);
        }
        else {
          createSoftRestoreAuditLog(entityAfter, idAfter);
        }
      }
      else {
        createUpdateAuditLog(entityAfter, idAfter, fieldMap);
      }
    }
  }

  @Transactional
  public void createInsertAuditLog(Object entity, Object entityId) throws IllegalAccessException {
    auditLogRepository.save(createAuditLog(entity, entityId, AuditedAction.INSERT));
  }

  @Transactional
  public void createSoftDeleteAuditLog(Object entity, Object entityId) throws IllegalAccessException {
    auditLogRepository.save(createAuditLog(entity, entityId, AuditedAction.SOFT_DELETE));
  }

  @Transactional
  public void createSoftRestoreAuditLog(Object entity, Object entityId) throws IllegalAccessException {
    auditLogRepository.save(createAuditLog(entity, entityId, AuditedAction.SOFT_RESTORE));
  }

  @Transactional
  public void createPermanentDeleteAuditLog(Object entity, Object entityId) throws IllegalAccessException {
    auditLogRepository.save(createAuditLog(entity, entityId, AuditedAction.DELETE));
  }

  @Transactional
  public void createPermanentDeleteAuditLog(Object id, Class<?> c, Object callingContext)
      throws ClassNotFoundException, IllegalAccessException, InvocationTargetException {

    try {
      Type superClass = c.getGenericSuperclass();
      if(superClass instanceof ParameterizedType) {
        for(Type type : ((ParameterizedType) superClass).getActualTypeArguments()) {
          Class<?> clazz = Class.forName(type.getTypeName());
          if(clazz.isAnnotationPresent(PersistentLogAudit.class)) {
            //TODO cache all the above
            Object entityBefore = getEntityBeforeFlush(id, callingContext);
            AuditLog deleteLog;
            if(entityBefore != null) {
              deleteLog = createAuditLog(entityBefore, id, AuditedAction.DELETE);
            }
            else {
              deleteLog = createAuditLog(id, AuditedAction.DELETE, clazz.getSimpleName());
            }
            auditLogRepository.save(deleteLog);
          }
        }
      }
      else {
        //TODO cannot find classname to audit log. log this
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
  }

  @Transactional
  public void createUpdateAuditLog(Object entity, Object entityId, FieldMap fieldMap) throws JsonProcessingException, IllegalAccessException {
    try {
      List<AffectedField> affectedFields = fieldMap.getAllAffectedEntries();
      if(!affectedFields.isEmpty()) {
        AuditLog auditLog = createAuditLog(entity, entityId, AuditedAction.UPDATE);
        String affectedFieldsJson = objectMapper.writeValueAsString(affectedFields);
        auditLog.setAffectedFields(affectedFieldsJson);
        auditLogRepository.save(auditLog);
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
  }

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  public Object getEntityBeforeFlush(Object id, Object callingContext) throws InvocationTargetException, IllegalAccessException {
    if(isNull(id)) {
      return null;
    }
    Method findById = reflectionCache.getFindByIdMethodForClass(callingContext.getClass());
    if(findById != null) {
      return ((Optional<?>) (findById.invoke(callingContext, id))).orElse(null);
    }

    return null;
  }


  public Object getEntityId(Object entity) throws IllegalAccessException {
    if(isNull(entity)) {
      return null;
    }

    Field idField = reflectionCache.getIdForClass(entity.getClass());
    if(isNull(idField)) {
      throw new IllegalStateException("No @Id found for entity: " + entity.getClass().getName());
    }

    return idField.get(entity);
  }


  private void putFieldsBefore(FieldMap fieldMap, Object entityBefore) {
    if(fieldMap == null || entityBefore == null) {
      return;
    }

    reflectionCache.getLoggableFieldsForClass(entityBefore.getClass())
        .forEach(f -> {
          try {
            fieldMap.putBefore(f.getName(), f.get(entityBefore));
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          }
        });
  }

  private void putFieldsAfter(FieldMap fieldMap, Object entityAfter) {
    if(fieldMap == null || entityAfter == null) {
      return;
    }

    reflectionCache.getLoggableFieldsForClass(entityAfter.getClass())
        .forEach(f -> {
          try {
            fieldMap.putAfter(f.getName(), f.get(entityAfter));
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          }
        });
  }

  private AuditLog createAuditLog(Object entity, Object entityId, AuditedAction auditedAction) throws IllegalAccessException {
    return AuditLog
        .builder()
        .action(auditedAction)
        .entityId(String.valueOf(entityId))
        .entityNaturalId(naturalIdValueOrNull(entity))
        .entityClassName(entity.getClass().getSimpleName())
        .auditor(auditorAwareBean.getCurrentAuditor().orElse("SYSTEM"))
        .dateTime(Instant.now())
        .build();
  }

  private AuditLog createAuditLog(Object entityId, AuditedAction auditedAction, String className)  {
    return AuditLog
        .builder()
        .action(auditedAction)
        .entityId(String.valueOf(entityId))
        .entityClassName(className)
        .auditor(auditorAwareBean.getCurrentAuditor().orElse("SYSTEM"))
        .dateTime(Instant.now())
        .build();
  }

  private String naturalIdValueOrNull(Object entity) throws IllegalAccessException {
    if(entity == null) {
      return null;
    }
    Field naturalIdField = reflectionCache.getNaturalIdForClass(entity.getClass());
    String naturalId = null;
    if(naturalIdField != null) {
      naturalId = String.valueOf(naturalIdField.get(entity));
    }

    return naturalId;
  }

}
