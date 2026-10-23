# ERP comparison: Bling and Tiny (Olist ERP)

Roadmap step 0. Sources read on 2026-10-23 from official documentation only.
Anything not confirmed in those pages is marked **unverified**.

Versions covered:
- **Bling API v3** (OpenAPI spec published by Bling, `info.version` 3.0). Base URL `https://api.bling.com.br/Api/v3`.
- **Tiny / Olist ERP API v3** (spec version 3.1, changelog 3.1.12 of Oct 2026). Base URL `https://api.tiny.com.br/public-api/v3`.
  Tiny API v2 still works "with no estimated discontinuation date" but gets no new features. Not considered.

## Summary table

| Topic | Bling v3 | Tiny / Olist ERP v3 |
|---|---|---|
| **Auth** | OAuth2 authorization code. `authorization_code` expires in 1 min. Client credentials sent via HTTP Basic on `/oauth/token`. Token revocation endpoint `/oauth/revoke`. | OAuth2 authorization code via Keycloak (`accounts.tiny.com.br/realms/tiny/...`). `client_id`/`client_secret` in the form body. |
| **Access token lifetime** | Doc example shows `expires_in: 21600` (6 h). Real value comes in the response; 6 h itself is **unverified**. | 4 hours (stated). |
| **Refresh token lifetime** | 30 days (stated). | **1 day** (stated). An account that does not sync for over a day needs a new user authorization. |
| **Refresh token rotation** | **unverified** (docs do not say whether the old refresh token is invalidated). | **unverified**. |
| **Token format** | Opaque tokens are deprecated; JWT via header `enable-jwt: 1` on `/oauth/token` and on every refresh. JWT is 1,500 to 3,000 chars. Cutoff date for opaque tokens: "being defined". | Bearer token; format not documented. |
| **Rate limits** | Per account, all endpoints: 3 req/s and 120,000 req/day. 429 on excess. IP blocks: 300 errors/10 s or 600 req/10 s, 10 min block; 20 `/oauth/token` calls/60 s, 60 min block; repeat offenders may be blocked indefinitely. Retry-wait header: **unverified**. | Per account (shared by all apps of the account), per minute, depends on the plan: 30 read / 30 write (Construa, Crescer, Partner plans) up to 140 read / 100 write (Protagonize, Potencializar). Headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (seconds to reset). Status code on excess: **unverified** (doc says "returns an error"). |
| **Order list endpoint** | `GET /pedidos/vendas` | `GET /pedidos` |
| **Filter by update date** | Yes: `dataAlteracaoInicial` / `dataAlteracaoFinal`, format `yyyy-MM-dd HH:mm:ss`. Any period filter wider than 1 year returns 400. | Yes: `dataAtualizacao` (single value, string). Format and semantics (`>=`? exact day?) **unverified** in v3. v2 used `dd/mm/yyyy hh:mm:ss`. |
| **Timezone of dates** | Not documented: **unverified** (probably Brasília local time). | Not documented: **unverified**. |
| **Pagination** | Page number: `pagina` (from 1), `limite` (default 100, max **unverified**). No total in the response. | Offset: `offset`, `limit` (default 100, max **unverified**). Response carries `paginacao { limit, offset, total }`. `orderBy=asc|desc`, sort field **unverified**. |
| **Update timestamp in the response** | **None** in list or detail. Filtering by change date works, but the order does not say when it changed. | **None** in list or detail. Same problem. |
| **Creation date** | `data` is a date without time. | List: `dataCriacao` (string, format unverified). Detail: `data` is a date without time. |
| **Order detail** | `GET /pedidos/vendas/{id}`: items (code, description, qty, unit price, **item discount as a percentage**), installments (`parcelas`: due date, amount, `formaPagamento.id`), freight, discount (`valor` + `unidade`), total, contact (id, name, person type, CPF/CNPJ), `loja.id`, `notaFiscal.id`, intermediary CNPJ. | `GET /pedidos/{id}`: items (SKU, description, qty, unit price; **no item discount**), payment (receipt method name, payment method name, installments), integrated payments, discount, freight, total, customer (name, person type, CPF/CNPJ, phone, mobile, e-mail, address), `ecommerce` block, invoice (number, series, **access key**). |
| **Extra calls needed** | Yes. Contact e-mail/phone: `GET /contatos/{id}`. Status name: `situacao` only has `id`/`valor`, name via `/situacoes`. Sales channel type: `GET /canais-venda/{loja.id}`. Payment method name: `/formas-pagamentos/{id}`. NF-e access key: `/nfe/{id}`. Lookups can be cached per run. | List needs one detail call per order (list has no items or payments). Otherwise detail is self-contained. |
| **Sales channel field** | `loja.id` → `/canais-venda/{id}` returns `descricao` and `tipo` (string; list of values via `/canais-venda/tipos`, values **unverified**). Orders with no store: `loja.id` 0 or absent (**unverified**). Reliability: **unverified**, needs a test account. | `ecommerce { id, nome, numeroPedidoEcommerce, numeroPedidoCanalVenda, canalVenda }` plus `origemPedido` (0 = sales order, 1 = POS). Meaning of `canalVenda` values: **unverified**. Reliability: **unverified**. |
| **Status** | Custom per account (`situacao.id`, names via `/situacoes`). Canonical mapping needs the account's status list. | Fixed enum: 8 Incomplete data, 0 Open, 3 Approved, 4 Preparing shipment, 1 Invoiced, 7 Ready to ship, 5 Shipped, 6 Delivered, 10 Being returned, 2 Cancelled, 9 Not delivered. |
| **Money type in API** | `number` / `float` | `number` / `float` (list `valor` is a **string**) |
| **Webhooks** | Yes, per app. Order resource `order`, actions `created`/`updated`/`deleted`. HMAC-SHA256 signature in `X-Bling-Signature-256`. No ordering guarantee, may repeat. Retries for up to 3 days, 2xx within 5 s, then the webhook is disabled. | Yes, per account (not per app), only on some plans. "Sales notifications" on order create/update. Up to 10 retries, +5 min each. Signature: **unverified** (not documented). |
| **Sandbox / test account** | No sandbox found. OpenAPI lists a "documentation test environment" (`developer.bling.com.br/api/bling`) for trying calls from the docs. Free developer account: **unverified**. | No sandbox. API needs the "Construa" plan or higher. Homologated partners get a test account ("Partner plan"). Free developer account: **unverified**. |
| **Distribution model** | Private apps, or public apps after homologation (automated API test plus manual review). | Own-account use: private app, no homologation. Selling or providing to third parties: homologation **mandatory**. Today only private apps exist in v3: each seller creates the app in their own account and hands `client_id`/`client_secret` to the integrator. Public apps "coming soon". |
| **Terms of use** | [bling.com.br/termos-de-uso](https://www.bling.com.br/termos-de-uso): page loads its text via JS and could not be read. Content **unverified**. | [ERP terms](https://olist.com/seguranca-e-privacidade/termos-de-uso-erp/): forbid reverse engineering (2.2). No clause about public APIs, third-party clients or open-source publication. |
| **Publishing an open-source client allowed?** | Not addressed in what was read. Needs written vendor confirmation (rule 7). | Not addressed in the terms. Needs written vendor confirmation (rule 7). Contact: `erp.partners@olist.com`. |
| **Restrictions on storing or passing on data** | **unverified** | ERP terms only cover Olist's own data processing (LGPD annex). Nothing specific to integrators: **unverified**. |
| **API stability** | Public changelog with dated versions since v2.7.8 (2023), regular releases in 2026. Breaking changes already happened: base URL moved to `api.bling.com.br`, opaque → JWT migration underway. | Public changelog. v3.1 (Jan 2026) to v3.1.12 (Oct 2026), mostly additive. v2 frozen. |

## Findings that affect the design (for the maintainer)

1. **Neither ERP returns `updatedAt`.** Both filter by change date, but the order payload has no change timestamp.
   `updatedAt` in the canonical model would have to stay unknown, or be filled with something that is not the real value (which rule 5 forbids).
   Dedup "by `updatedAt` to pick the newest" (sync semantics) does not work as written.
2. **`createdAt` is a date only** in both detail payloads, and no timezone is documented.
   `Instant` in UTC would require assuming a time and a zone. Proposal to discuss: `LocalDate` + raw string, or a nullable `Instant`.
3. **The cursor cannot be a pure timestamp taken from the data** (item 1). It has to be the request window the caller passed plus the page.
   Page/offset pagination over a set that changes during the read can skip or repeat orders, which supports the overlap window and at-least-once delivery.
4. **Tiny refresh token lasts 1 day.** The `TokenStore` must persist the new token on every refresh, and the consumer must sync at least daily or ask the user to re-authorize. Refresh-token rotation is unverified on both.
5. **Bling needs N+1 calls** (contact, status names, channel type, payment names). At 3 req/s this dominates the cost of a sync. Lookups (status, channel, payment method) can be cached for the duration of one connector call without breaking the "no global state" rule.
6. **Item discount**: Bling gives it as a percentage, Tiny does not give it. The model needs "discount unknown" per item.
7. **Status**: Bling statuses are configured per account, so the canonical mapping cannot be hard-coded. Tiny uses a fixed enum.
8. **Neither vendor's terms say anything about open-source clients.** Rule 7 still applies: written confirmation from both before publishing a connector.

## Still unverified (needs a test account or a question to the vendor)

- Maximum page size on both; sort field of Tiny `orderBy`.
- Format and semantics of Tiny `dataAtualizacao`; timezone of every date on both.
- Refresh-token rotation on both; Bling's real `expires_in`.
- Values of Bling `canais-venda` `tipo` and Tiny `ecommerce.canalVenda`, and how reliably they are filled.
- Retry-after information on 429 for Bling; Tiny's status code on rate limit.
- Bling terms of use content; data storage/passing restrictions on both.
- Free developer/test accounts on both.

## Sources

Bling:
- https://developer.bling.com.br/aplicativos (OAuth, token lifetimes, revocation)
- https://developer.bling.com.br/migracao-jwt
- https://developer.bling.com.br/limites
- https://developer.bling.com.br/boas-praticas (pagination)
- https://developer.bling.com.br/webhooks
- https://developer.bling.com.br/homologacao
- https://developer.bling.com.br/referencia (OpenAPI spec loaded by that page)
- https://developer.bling.com.br/changelogs
- https://www.bling.com.br/termos-de-uso

Tiny / Olist ERP:
- https://api-docs.erp.olist.com/documentacao/comecando/autenticacao
- https://api-docs.erp.olist.com/documentacao/comecando/limites-de-consulta
- https://ajuda.olist.com/hubs-e-plataformas-via-api/aplicativos-api-v3-configuracoes-e-utilizacao (limits per plan, plan requirement)
- https://api-docs.erp.olist.com/documentacao/webhooks/webhooks
- https://api-docs.erp.olist.com/documentacao/clientes/orientacoes
- https://api-docs.erp.olist.com/documentacao/parceiros/homologacao
- https://api-docs.erp.olist.com/api-reference/pedidos/listar-pedidos
- https://api-docs.erp.olist.com/api-reference/pedidos/obter-pedido
- https://api-docs.erp.olist.com/changelog/versao-3-1 and versao-3-1-12
- https://tiny.com.br/api-docs/api (v2 status)
- https://olist.com/seguranca-e-privacidade/termos-de-uso-erp/
