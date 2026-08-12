package com.odoomaster.ticketing.audit;

import com.odoomaster.ticketing.audit.internal.AuditLog;
import com.odoomaster.ticketing.audit.internal.AuditLogRepository;
import com.odoomaster.ticketing.shared.Auditable;
import com.odoomaster.ticketing.shared.AuthPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * AOP aspect that persists an audit row after any {@link Auditable}-annotated method succeeds.
 *
 * <p>After the target returns, it records the {@code action}/{@code entity} from the annotation,
 * the current user (from the security context), the returned entity id (via {@code id()}/{@code getId()}),
 * and the request trace id. Audit failures are swallowed (logged) so they never break the business call.
 */
@Aspect
@Component
@Slf4j
public class AuditAspect {

    private final AuditLogRepository audits;

    public AuditAspect(AuditLogRepository audits) {
        this.audits = audits;
    }

    /**
     * Around advice that writes the audit row after the target method returns.
     *
     * @param pjp the intercepted join point
     * @return the target method's result (returned unchanged)
     * @throws Throwable if the target method throws
     */
    @Around("@annotation(com.odoomaster.ticketing.shared.Auditable)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            Method m = sig.getMethod();
            Auditable a = m.getAnnotation(Auditable.class);
            if (a == null) return result;

            Long entityId = extractId(result);
            Long userId = currentUserId();
            String traceId = MDC.get("traceId");
            audits.save(AuditLog.of(userId, a.action(), a.entity(), entityId, null, traceId, Instant.now()));
        } catch (Exception e) {
            log.warn("audit aspect failed: {}", e.toString());
        }
        return result;
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return p.userId();
        }
        return null;
    }

    private Long extractId(Object value) {
        if (value == null) return null;
        try {
            var m = value.getClass().getMethod("id");
            Object v = m.invoke(value);
            if (v instanceof Long l) return l;
            if (v instanceof Number n) return n.longValue();
        } catch (NoSuchMethodException ignore) {
            try {
                var m = value.getClass().getMethod("getId");
                Object v = m.invoke(value);
                if (v instanceof Long l) return l;
                if (v instanceof Number n) return n.longValue();
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return null;
    }
}
