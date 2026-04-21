package com.workflow.bpm.workflow.engine;

import com.workflow.bpm.shared.model.Transition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TransitionEvaluator {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    /**
     * Evalúa si una transición aplica dados los valores actuales del proceso.
     * condition null o vacía = transición siempre válida (flujo secuencial).
     *
     * Ejemplos de condiciones válidas:
     *   "tieneDeuda == false"
     *   "monto > 1000"
     *   "resultado == 'APROBADO'"
     *   "factibilidad == true and monto <= 500"
     */
    public boolean evaluate(String condition, Map<String, Object> variables) {
        if (condition == null || condition.isBlank()) {
            return true;
        }

        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            
            // Registrar cada variable en el contexto
            if (variables != null) {
                variables.forEach(ctx::setVariable);
            }

            // Normalizar: "tieneDeuda == false" → "#tieneDeuda == false"
            String spel = normalizeCondition(condition);
            
            Boolean result = parser.parseExpression(spel)
                                   .getValue(ctx, Boolean.class);
            
            log.debug("Condición '{}' → {} (vars disponibles: {})", 
                      condition, result, variables != null ? variables.keySet() : "none");
            
            return Boolean.TRUE.equals(result);

        } catch (EvaluationException | ParseException ex) {
            log.error("Error evaluando condición '{}': {}", condition, ex.getMessage());
            return false;  // condición inválida = no tomar esta transición
        }
    }

    /**
     * Elige la primera transición cuya condición sea verdadera.
     * Para nodos DECISION siempre debe haber exactamente una que aplique.
     * Para nodos FORK devuelve TODAS las que apliquen (flujo paralelo).
     */
    public List<Transition> resolveTransitions(
            List<Transition> candidates,
            Map<String, Object> variables,
            boolean all) {

        return candidates.stream()
            .filter(t -> evaluate(t.getCondition(), variables))
            .limit(all ? Long.MAX_VALUE : 1)
            .collect(Collectors.toList());
    }
    
   // Agregar este método a TransitionEvaluator.java

    /**
     * Encuentra la transición por defecto (sin condición)
     */
    public Transition findDefaultTransition(List<Transition> candidates) {
        return candidates.stream()
                .filter(t -> t.getCondition() == null || t.getCondition().isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Agrega # a variables no prefijadas: "monto > 0" → "#monto > 0"
     */
    private String normalizeCondition(String condition) {
        if (condition == null || condition.isBlank()) {
            return condition;
        }
        
        // Patrón simple: agrega # a palabras que parecen variables
        // Una implementación más robuste usaría un parser de SpEL real
        return condition.replaceAll("(?<![#'\"])\\b([a-zA-Z][a-zA-Z0-9_]*)\\b(?!['\"]\\s*:)", "#$1");
    }
}