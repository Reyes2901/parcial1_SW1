# AGENTS.md

## 1) Project Overview

Sistema backend BPM para gestionar tramites/workflows con:
- definicion de politicas de proceso (nodos, transiciones, lanes),
- inicio y seguimiento de instancias,
- ejecucion de tareas con formularios dinamicos,
- carga y firma de archivos,
- analitica operativa (dashboard, cuellos de botella, carga por departamento),
- notificaciones en tiempo real por WebSocket,
- autenticacion JWT y control por roles.

Evidencia principal:
- `src/main/java/com/workflow/bpm/BpmApplication.java`
- controladores en `src/main/java/com/workflow/bpm/**/**Controller.java`
- motor de workflow custom en `src/main/java/com/workflow/bpm/workflow/engine/`

## 2) Tech Stack Real

Backend:
- Java 17 (`pom.xml`)
- Spring Boot 3.5.13 (`pom.xml`)
- Spring Web (`spring-boot-starter-web`)
- Spring Security + JWT (jjwt 0.11.5)
- Spring Data MongoDB (`spring-boot-starter-data-mongodb`)
- Spring WebSocket + STOMP (`spring-boot-starter-websocket`)
- Spring WebFlux WebClient (`spring-boot-starter-webflux`) para consumo de servicio IA
- Bean Validation (`spring-boot-starter-validation`)
- OpenAPI/Swagger (`springdoc-openapi-starter-webmvc-ui`)
- Lombok

Infra/config detectada:
- MongoDB por URI externa (`src/main/resources/application.yml`, `.env`)
- Puerto backend 8080 (`src/main/resources/application.yml`)
- Servicio IA externo via URL configurable (`app.ai-service.url`, default `http://localhost:8000`)

Frontend en este workspace:
- No existe codigo frontend Angular/React/Vue dentro de este repositorio.
- No existen `package.json`, `angular.json`, ni carpeta frontend dedicada.
- Solo hay referencias indirectas a Angular en comentarios y CORS hacia `http://localhost:4200`.

## 3) Arquitectura Frontend (ShellComponent, routing, modulos)

Estado real en este repo:
- No existe `ShellComponent`.
- No existe archivo de routing frontend (`app.routes.ts`, `app-routing.module.ts`, etc.).
- No existen modulos de frontend (`modules`, `services` de Angular) dentro del workspace actual.

Lo unico observable desde backend:
- CORS permite `http://localhost:4200` (`SecurityConfig` y `CorsConfig`).
- Comentarios en controladores indican consumo por Angular, pero el codigo Angular no esta en este repositorio.

Implicacion para agentes:
- Cualquier cambio de frontend no puede implementarse ni verificarse aqui.
- Este repo debe tratarse como backend API + websocket + motor BPM.

## 4) Integracion con API (endpoints reales)

Autenticacion:
- `POST /auth/login`
- `POST /api/auth/login`

Usuarios:
- `POST /users`
- `GET /users`
- `GET /users/{id}`

Politicas (`/api/policies`):
- `POST /api/policies`
- `PUT /api/policies/{id}`
- `POST /api/policies/{id}/publish`
- `POST /api/policies/{id}/activate`
- `POST /api/policies/{id}/archive`
- `DELETE /api/policies/{id}`
- `GET /api/policies`
- `GET /api/policies/active`
- `GET /api/policies/{id}`
- `GET /api/policies/my-drafts`
- `GET /api/policies/my-policies`
- `POST /api/policies/ai/generate`

Workflow (`/api/workflow`):
- `POST /api/workflow/start`
- `GET /api/workflow/instances/{id}`
- `GET /api/workflow/instances/my-requests`
- `GET /api/workflow/tasks/my-tasks`
- `POST /api/workflow/tasks/{id}/start`
- `POST /api/workflow/tasks/{id}/complete`
- `POST /api/workflow/tasks/{id}/reject`
- `POST /api/workflow/instances/{id}/cancel`
- `GET /api/workflow/test/ws`
- `POST /api/workflow/admin/trigger-bottleneck-check`
- `GET /api/workflow/admin/stats`
- `GET /api/workflow/instances/{id}/history`

Formularios de tarea (`/api/tasks`):
- `GET /api/tasks/{id}/form`
- `POST /api/tasks/{id}/form`
- `PUT /api/tasks/{id}/form/save-draft`

Archivos (`/api/files`):
- `POST /api/files/upload`
- `POST /api/files/upload/multiple`
- `GET /api/files/**`
- `DELETE /api/files/**`
- `POST /api/files/signature`

Analytics (`/api/analytics`):
- `GET /api/analytics/dashboard`
- `GET /api/analytics/policies/{id}/stats`
- `GET /api/analytics/bottlenecks`
- `GET /api/analytics/department-load`
- `GET /api/analytics/users/{userId}/performance`

WebSocket STOMP:
- Endpoint handshake SockJS: `/ws`
- Broker topics/queues usados:
  - `/topic/admin/completed`
  - `/topic/admin/rejected`
  - `/topic/admin/bottlenecks`
  - `/user/{user}/queue/tasks`
  - `/user/{user}/queue/instance-status`

Integracion externa detectada:
- FastAPI IA: `POST {app.ai-service.url}/ai/generate-diagram` desde `AiService`.

## 5) Flujo de datos del dashboard (importante)

Endpoint de entrada:
- `GET /api/analytics/dashboard` en `AnalyticsController.getDashboard()`.

Orquestacion de `AnalyticsService.getDashboard()`:
1. Cuenta instancias activas desde `ProcessInstanceRepository.countByStatus("IN_PROGRESS")`.
2. Obtiene completadas/rechazadas del dia via `AnalyticsRepository.getCompletedToday()` y `getRejectedToday()`.
3. Cuenta tareas vencidas via `TaskInstanceRepository.countByStatusInAndDueAtBefore([PENDING, IN_PROGRESS], now)`.
4. Calcula tasa global de completacion:
   - total instancias (`instanceRepo.count()`)
   - completadas (`instanceRepo.countByStatus("COMPLETED")`).
5. Calcula `avgResolutionHours` con agregacion Mongo (`AnalyticsRepository.getAvgResolutionHours()`).
6. Obtiene cuellos de botella activos con agregacion Mongo (`getActiveBottlenecks()` sobre `task_instances`), limita a 10 y mapea a `BottleneckReport`.
7. Obtiene top politicas por uso (`getTopPoliciesByUsage(3)` sobre `process_instances`) y mapea a `PolicyUsage`.
8. Obtiene carga por departamento (`getDepartmentLoad()` sobre `task_instances`) y mapea a `DepartmentLoad`.
9. Arma `DashboardSummary` con todos los bloques.

Colecciones Mongo usadas por analytics:
- `task_instances`
- `process_instances`

Salida consolidada:
- DTO `DashboardSummary` con campos:
  - `totalActiveInstances`
  - `totalCompletedToday`
  - `totalRejectedToday`
  - `totalOverdueTasks`
  - `globalCompletionRatePct`
  - `avgResolutionHours`
  - `topPolicies`
  - `activeBottlenecks`
  - `departmentLoad`

## 6) Problemas actuales detectados

1. Secretos en repositorio:
- `.env` contiene credenciales Mongo y secretos JWT en texto plano.
- `application.yml` contiene `jwt.secret` hardcodeado.

2. Exposicion de datos sensibles:
- `UserController.create()` hace `System.out.println("USER RECIBIDO: " + user)`.
- `UserController.getAll()` devuelve usuarios sin nullear password (linea para ocultar password esta comentada).

3. Seguridad WebSocket incompleta:
- Existe `WebSocketAuthInterceptor`, pero su registro en `WebSocketConfig.configureClientInboundChannel` esta comentado.
- `registerStompEndpoints` usa `setAllowedOriginPatterns("*")` para `/ws`.

4. Configuracion CORS duplicada:
- CORS definido tanto en `SecurityConfig` como en `CorsConfig`.
- Riesgo de comportamiento inconsistente segun orden de filtros/config.

5. Regla de seguridad posiblemente incorrecta/inutil:
- Se permite `POST /api/users` en `SecurityConfig`, pero el controlador real expone `POST /users`.

6. Side effect al arrancar aplicacion:
- `BpmApplication` inserta siempre un documento de prueba (`TestConnection`) en startup.

7. Archivo de prueba con token hardcodeado:
- `test.html` incluye un JWT pegado y scripts CDN; no esta integrado al build de backend.

8. Frontend no versionado en este repo:
- No hay codigo UI real para validar problemas de Tailwind/bindings/routing.
- Solo se puede confirmar backend listo para ser consumido por un frontend externo.

## 7) Reglas para futuros agentes (que NO romper)

1. No cambiar contratos de endpoints existentes sin migracion compatible:
- Mantener rutas/methods de `AuthController`, `PolicyController`, `WorkflowController`, `FormController`, `FileController`, `AnalyticsController`.

2. No romper el flujo analitico:
- Si se modifican estados (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`) o nombres de colecciones/campos Mongo, actualizar todas las agregaciones de `AnalyticsRepository` y mapeos de `AnalyticsService`.

3. No romper integracion IA:
- `AiService` depende de `{app.ai-service.url}/ai/generate-diagram` y timeout de 30s.

4. No romper notificaciones realtime:
- Respetar destinos STOMP actuales (`/topic/admin/*`, `/user/.../queue/*`) para evitar regressiones en clientes.

5. Si se toca seguridad:
- Conservar JWT stateless.
- Revisar impacto en `@PreAuthorize` de endpoints ADMIN.
- Validar CORS y WebSocket auth en conjunto (no por separado).

6. No introducir logs con credenciales, JWT ni datos sensibles.

7. No asumir frontend dentro de este repo:
- Cualquier decision de ShellComponent/routing/modules frontend requiere otro repositorio o carpeta no presente aqui.

## 8) Comandos de desarrollo

Windows (PowerShell) desde raiz del repo:
- `./mvnw.cmd clean install`
- `./mvnw.cmd spring-boot:run`
- `./mvnw.cmd test`

Alternativa (si Maven global esta instalado):
- `mvn clean install`
- `mvn spring-boot:run`
- `mvn test`

Endpoints de soporte:
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Dependencias externas necesarias para funcionalidad completa:
- MongoDB accesible por `MONGO_URI`
- Servicio IA en `AI_SERVICE_URL` (por defecto `http://localhost:8000`)

## 9) Guia de debugging

Checklist rapido de arranque:
1. Verificar variables:
- `MONGO_URI`
- `AI_SERVICE_URL` (si se usa generacion IA)
- `JWT_SECRET` y expiracion coherente

2. Iniciar backend:
- `./mvnw.cmd spring-boot:run`
- Confirmar escucha en puerto 8080

3. Probar auth:
- `POST /auth/login` o `POST /api/auth/login`
- Usar token en `Authorization: Bearer <token>`

4. Probar analytics:
- `GET /api/analytics/dashboard` (requiere ADMIN)
- Si da vacio o cero, revisar datos en colecciones `process_instances` y `task_instances`.

5. Probar workflow minimo:
- crear/activar politica,
- `POST /api/workflow/start`,
- consultar `GET /api/workflow/instances/{id}`,
- listar `GET /api/workflow/tasks/my-tasks`.

6. Probar WebSocket:
- handshake en `/ws`.
- `GET /api/workflow/test/ws` publica en `/topic/test` para smoke test.
- si no llegan eventos privados, revisar registro del interceptor WS (actualmente comentado).

7. Revisar logs:
- `application.yml` pone `com.workflow.bpm` y `org.springframework.security` en DEBUG.
- Priorizar errores en AuthFilter, AccessDenied, Mongo aggregations y WorkflowEngine.

8. Si falla IA:
- verificar conectividad a `AI_SERVICE_URL`.
- revisar excepcion envuelta en `WorkflowException` desde `AiService.generateDiagram()`.
