package gr.antopapak.persistent.audit.aspect;


import gr.antopapak.persistent.audit.service.AuditLogService;
import lombok.AllArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * An aspect that audits the mutation of entities annotated {@link gr.antopapak.persistent.audit.annotation.PersistentLogAudit}.
 *
 */
@Aspect
@Component
@Order(0)
@EnableAspectJAutoProxy
@AllArgsConstructor
public class PersistentAuditAspect {

  private final AuditLogService auditLogService;

  @Pointcut("execution(public * save(..))")
  public void saveMethods() {}

  @Pointcut("@args(com.creds.api.audit.annotation.PersistentLogAudit)")
  public void methodsAcceptingPersistentLogAudit() {}

  @Pointcut("saveMethods() && methodsAcceptingPersistentLogAudit()")
  public void persistentLogAuditSaveMethods() {}

  @Around("persistentLogAuditSaveMethods()")
  @Transactional
  public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
    try {
      Object entityArg = joinPoint.getArgs()[0];
      Object idBefore = auditLogService.getEntityId(entityArg);
      Object entityBefore = auditLogService.getEntityBeforeFlush(idBefore, joinPoint.getThis());

      Object result = joinPoint.proceed();

      auditLogService.createNonDeletingAudit(entityBefore, result);

      return result;
    } catch (Throwable e) {
      e.printStackTrace();
      throw e;
    }
  }


  @Pointcut("execution(public * delete(..))")
  public void deleteMethods() {}

  @Pointcut("execution(public * deleteById(..))")
  public void deleteByIdMethods() {}

  @Pointcut("deleteMethods() && methodsAcceptingPersistentLogAudit()")
  public void persistentLogAuditDeleteMethods() {}

  @After("persistentLogAuditDeleteMethods()")
  @Transactional
  public void auditDelete(JoinPoint joinPoint) throws Throwable {
    Object entityArg = joinPoint.getArgs()[0];
    Object id = auditLogService.getEntityId(entityArg);
    auditLogService.createPermanentDeleteAuditLog(entityArg, id);
  }

  @After("deleteByIdMethods()")
  @Transactional
  public void auditDeleteById(JoinPoint joinPoint) throws Throwable {
    Object entityId =  joinPoint.getArgs()[0];
    Class<?> c = joinPoint.getSignature().getDeclaringType();
    auditLogService.createPermanentDeleteAuditLog(entityId, c, joinPoint.getThis());
  }

}
