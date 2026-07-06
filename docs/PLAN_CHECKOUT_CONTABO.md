# Plan de alineación Checkout ↔ Contabo

> Referencia de implementación para empaquetar planes como Contabo, cobrar con margen Valora y aprovisionar correctamente.

## Objetivo

Unificar precios entre quote, checkout y UI web; mapear cada variante de plan a los `productId` de Contabo (NVMe/SSD/Storage); enviar `addOns` reales al crear instancias; exponer campos faltantes en admin.

---

## Modelo Contabo (referencia)

| Dimensión | API Contabo | Ejemplo |
|-----------|-------------|---------|
| Tamaño + disco | `productId` | VPS 20 NVMe=`V94`, SSD=`V95`, Storage=`V96` |
| Región | `region` | `EU`, `US-east`, `SIN` |
| Compromiso | `period` | 1, 3, 6, 12 meses |
| SO | `imageId` | UUID Linux o slug Windows |
| Extras | `addOns` | `{ "privateNetworking": {}, "backup": {} }` |
| Panel | `license` | Plesk/cPanel (opcional) |

Cada tier VPS tiene **3 productIds**. Windows usa el mismo `productId` que Linux; cambia `imageId`.

---

## Arquitectura Valora (target)

```
addon_catalog          → metadata + contaboValue (EU, auto-backup, …)
plan.availableAddons   → precios por plan (priceMonthly, regionPrices)
Plan                   → specs + price1/6/12 + contaboPlanId (+ SSD/Storage/Windows)
PricingService         → única fuente de cálculo (quote + checkout)
ContaboProvisioningResolver → productId + addOns desde orden
ProvisioningProcessor  → createInstance con addOns
```

---

## Fase 1 — Backend: Pricing unificado

### Archivos

| Acción | Archivo |
|--------|---------|
| **Crear** | `plans/PricingService.kt` |
| **Modificar** | `plans/PlansService.kt` — delegar a PricingService |
| **Modificar** | `orders/OrdersService.kt` — delegar a PricingService, corregir total Stripe |

### Reglas de precio

```
baseMonthly     = plan.price{N}Months según billingCycle (1→price1Month, 6→price6Months, 12→price12Months)
setupFee        = plan.setup{N}Months
addonMonthly    = sum(planAddon.regionPrices[regionAddonId] ?: planAddon.priceMonthly)
totalMonthly    = baseMonthly + addonMonthly
subtotal        = totalMonthly × billingCycle
totalAmount     = subtotal + setupFee   ← bug corregido (antes base no se multiplicaba)
```

### Campos en Order (semántica)

| Campo | Valor |
|-------|-------|
| `basePrice` | Tarifa mensual base del término elegido |
| `addonsPrice` | Suma addons × billingCycle |
| `setupFee` | Setup del término |
| `totalAmount` | `(basePrice + addonsMonthly) × billingCycle + setupFee` |

### Tests

- `PricingServiceTest.kt`: ciclos 1/6/12, regionPrices, setup, total
- Caso regresión: 12 meses base $10 + addon $2/mo → total $144 + setup

---

## Fase 2 — Backend: Mapeo Contabo provisioning

### Archivos

| Acción | Archivo |
|--------|---------|
| **Crear** | `provisioning/ContaboProvisioningResolver.kt` |
| **Modificar** | `contabo/ContaboTypes.kt` — `ContaboCreateInstanceAddOns`, `license` |
| **Modificar** | `contabo/ContaboService.kt` — pasar addOns/license |
| **Modificar** | `provisioning/processor/ProvisioningProcessor.kt` — usar resolver |

### Mapeo storage → productId

| Addon id (contiene) | Campo plan | Fallback |
|---------------------|------------|----------|
| `ssd` | `contaboPlanIdSsd` | `contaboPlanId` |
| `storage` (no nvme/ssd) | `contaboPlanIdStorage` | `contaboPlanId` |
| default / nvme | `contaboPlanId` | error si vacío |

### Mapeo addon_catalog.contaboValue → addOns API

| contaboValue | addOns JSON |
|--------------|-------------|
| `private-networking` | `{ "privateNetworking": {} }` |
| `auto-backup` | `{ "backup": {} }` |

Addons sin mapeo (monitoring, object_storage bundle) → no se envían; solo se cobran si están en el plan.

### Tests

- `ContaboProvisioningResolverTest.kt`: productId por storage, addOns desde lista de ids, región desde catalog

---

## Fase 3 — Backend: Admin + migración DB

### Archivos

| Acción | Archivo |
|--------|---------|
| **Crear** | `db/migration/V4__plan_contabo_product_ids.sql` |
| **Modificar** | `admin/dto/AdminDtos.kt` — contaboPlanIdSsd/Storage/Windows |
| **Modificar** | `admin/service/AdminService.kt` — persistir campos |

### Migración V4

```sql
ALTER TABLE plans ADD COLUMN IF NOT EXISTS contabo_plan_id_ssd VARCHAR(255);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS contabo_plan_id_storage VARCHAR(255);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS contabo_plan_id_windows VARCHAR(255);
```

---

## Fase 4 — Frontend Admin (`valoracloud-admin-dash`)

### Archivos

| Acción | Archivo |
|--------|---------|
| **Modificar** | `src/types/server.ts` — tipos CreatePlanDto/Plan |
| **Modificar** | `src/app/.../plans/plan-dialogs.tsx` — inputs SSD/Storage/Windows |

### UI

En crear/editar plan, sección **Contabo Product IDs**:

- Plan ID (NVMe) — required
- Plan ID (SSD) — optional
- Plan ID (Storage) — optional
- Plan ID (Windows) — optional (reservado; mismo productId que Linux en API)
- Contabo Cost ($) — referencia de margen

---

## Fase 5 — Frontend Web (`valora-launchpad`)

### Archivos

| Acción | Archivo |
|--------|---------|
| **Modificar** | `src/lib/pricing.ts` — regionPrices, alinear fórmula con backend |
| **Modificar** | `package.json` — vitest + script test |
| **Crear** | `src/lib/pricing.test.ts` |

### Reglas frontend (deben coincidir con PricingService)

- `getBasePrice(plan, cycle)` → price1/6/12Months
- `addonMonthlyPrice(plan, addonId, regionAddonId)` → regionPrices[region] ?? priceMonthly
- `totalAmount = (base + addonsMonthly) × cycle + setupFee`
- No incluir `imageId` en addons de precio salvo que exista en `availableAddons` con precio

---

## Fase 6 — Tabla de mapeo Contabo (operaciones)

Documento vivo para admin al crear planes:

| Plan Valora | NVMe | SSD | Storage | Costo Contabo 1m | Margen 30% → price1Month |
|-------------|------|-----|---------|------------------|---------------------------|
| VPS 10 | V91 | V92 | V93 | €3.60 | $4.68 |
| VPS 20 | V94 | V95 | V96 | €5.60 | $7.28 |
| VPS 30 | V97 | V98 | V99 | €11.20 | $14.56 |

*(Precios Contabo orientativos; verificar en panel reseller.)*

---

## Checklist de verificación

- [x] `POST /plans/{id}/quote` y `POST /orders/checkout` usan `PricingService` (misma fórmula)
- [x] Stripe `amount` = total × 100 (base × ciclo + addons × ciclo + setup)
- [x] Instancia SSD usa `contaboPlanIdSsd` vía `ContaboProvisioningResolver`
- [x] Orden con `backup-auto` envía `addOns.backup` a Contabo
- [x] Orden con `networking-private` envía `addOns.privateNetworking`
- [x] Admin puede editar NVMe/SSD/Storage/Windows productIds
- [x] Tests Kotlin: `./gradlew.bat test` (10 tests)
- [x] Tests frontend: `npm test` en valora-launchpad (5 tests)

---

## Fuera de alcance (fase 2 futura)

~~Object storage bundle embebido en VPS~~ ✅ Fase 2  
~~Monitoring addon~~ ✅ Fase 2 (monitor interno Valora)  
~~Upgrade extraStorage / disco doble~~ ✅ Fase 2 (`addOns.extraStorage`)  
~~Sincronización automática de precios desde costos Contabo + margen~~ ✅ Fase 2

---

## Fase 2 — Addons avanzados + margen (implementado)

### Backend

| Componente | Descripción |
|------------|-------------|
| `V5__phase2_bundles_margin.sql` | `server_id` en object storage, `contabo_cost_price` en catalog, `margin_percent` en plans |
| `MarginPricingService` | `applyMargin`, `suggestPlanPrices`, `suggestAddonPrice` |
| `ContaboProvisioningResolver` | `extraStorage`, `resolveObjectStorageBundle`, `isMonitoringEnabled` |
| `ProvisioningProcessor` | Bundle OS post-VPS, monitor cada 30s si addon monitoring |
| Admin API | `POST .../pricing/suggest`, `.../pricing/apply`, `.../addons/apply-margin` |

### Mapeos nuevos

| Addon | Provisioning |
|-------|--------------|
| `storage-*-upgrade` | `addOns.extraStorage` en Contabo |
| `objstorage-250gb` … `1tb` | Crea Object Storage Contabo ligado al server (`serverId`) |
| `monitoring-enabled` | ServerMonitor interval 30s (Valora interno) |

### Margen

```
precio_venta = contabo_cost × (1 + margin% / 100)
```

- Margen default: `app.pricing.default-margin-percent` (30%)
- Override por plan: `marginPercent`
- Costos wholesale en `addon_catalog.contabo_cost_price`

### Admin UI

- Edit Plan: margen %, preview, apply margin (plan + addons)

### Tests

- `MarginPricingServiceTest`
- `ContaboProvisioningResolverTest` (bundle, extraStorage, monitoring)


## Orden de ejecución

1. PricingService + tests
2. OrdersService + PlansService
3. ContaboProvisioningResolver + ContaboTypes + ProvisioningProcessor + tests
4. Migración V4 + Admin DTOs/service
5. Admin UI
6. Frontend pricing + vitest
7. `./gradlew.bat test` + `npm test`
