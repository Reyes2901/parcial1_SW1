package com.workflow.bpm.policy.websocket;

// ═══════════════════════════════════════════════════════════════════════
// DEPRECADO: Este controlador ha sido reemplazado por
// PolicyCollaborationController en el paquete com.workflow.bpm.policy.
//
// La nueva implementación soporta:
//   - Discriminación de acciones por ActionType (DRAG, LOCK, UNLOCK, COMMIT)
//   - Reenvío en memoria sin DB para acciones livianas
//   - Persistencia asíncrona solo en ELEMENT_COMMIT
//   - Filtro anti-eco (el sender no recibe su propio mensaje)
//   - Limpieza automática de locks al desconectarse
//
// Ruta nueva: /app/policy/collaborate/{policyId}
// ═══════════════════════════════════════════════════════════════════════