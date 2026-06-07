---
name: init-backend
description: Update AGENTS.md (or the backend context rules) with strict architectural directives that permanently fix the collaborative diagram WebSocket/STOMP flow in this BPM backend. Use when asked to harden, document, or initialize backend real-time rules, or when invoked as /init-backend.
disable-model-invocation: true
---

# init-backend

## Purpose

Act as a Senior Software Architect. When this skill runs, permanently document the WebSocket/STOMP rules for the collaborative diagrammer by writing them into `AGENTS.md` (or the backend context rules), so no agent repeats the broken real-time flow again.

## Instructions

1. Open `AGENTS.md` at the repository root (if backend context rules live elsewhere, update that file instead).
2. Append a clearly titled section (e.g. `## Reglas WebSocket/STOMP del diagramador colaborativo`) containing the four directives below, verbatim.
3. Do not paraphrase or soften the directives. Keep the Spanish wording and structure intact.
4. Do not duplicate the section if it already exists; update it in place instead.
5. After writing, confirm the rules are present and consistent with `WebSocketConfig.java` and `PolicyCollaborationController.java`.

## Directives to write (verbatim)

### 1. Contexto del Error Crítico Detectado
- Los logs del sistema revelaron un conflicto de hilos: `More than one TaskExecutor bean found within the context, and none is named 'taskExecutor'`. Esto bloqueaba la ejecución asíncrona de la persistencia de las políticas.
- El flujo se quedaba atrapado en el canal de entrada (`boundChannel`), imprimiendo logs de "reenviado en memoria", pero NUNCA salía hacia el broker de transmisión.

### 2. Reglas de Configuración Obligatorias (`WebSocketConfig.java`)
- Se debe garantizar la existencia de un Bean de tipo 'TaskExecutor' marcado con `@Primary` y nombrado exactamente "taskExecutor". Esto resuelve la colisión entre el `clientInboundChannelExecutor` y el `clientOutboundChannelExecutor`.
- El broker simple debe tener habilitados los prefijos `/topic` y `/queue`.

### 3. Reglas del Controlador de Colaboración (`@MessageMapping`)
- **PROHIBIDO** limitar el flujo a procesamiento local o logs en consola ("reenviado en memoria"). Cada evento recibido en el canal de entrada DEBE generar un broadcast inmediato de salida.
- Es obligatorio inyectar `SimpMessagingTemplate`.
- Cada vez que se procese un evento del diagramador (`ELEMENT_LOCK`, `ELEMENT_DRAG`, `ELEMENT_UNLOCK`, `ELEMENT_COMMIT`), se debe despachar el DTO al tópico dinámico correcto usando:
  `messagingTemplate.convertAndSend("/topic/policy/" + payload.getPolicyId(), payload);`

### 4. Manejo de Persistencia Asíncrona
- El guardado del estado del diagrama en la base de datos (BPMN XML) debe seguir siendo asíncrono (`@Async`), pero jamás debe retrasar o bloquear el envío del mensaje por el `messagingTemplate`. El broadcast a los usuarios tiene prioridad de milisegundos para evitar lag visual en el frontend.

## Closing note

A partir de ahora, cualquier refactorización del sistema de tiempo real debe validar que se cumpla el broadcast de salida.
