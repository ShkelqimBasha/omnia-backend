# PayPal Live for order checkouts

Scope: order checkouts (`/api/paypal/**`). Listing promotions keep their own settings
(`omnia.promotions.paypal.*`) and are not changed by this.

## Configuration

| Setting | Default | Meaning |
|---|---|---|
| `omnia.paypal.enabled` | `false` | PayPal for orders on or off |
| `omnia.paypal.environment` | `sandbox` | `sandbox` or `live`; anything else disables PayPal |
| `omnia.paypal.credentials-file` | — | JSON with `clientId`, `clientSecret`, `merchantId`, `webhookId`, `environment` |
| `omnia.paypal.public-base` | `https://api.myomniaapp.com` | HTTPS base for the PayPal return URL |

Live is used only when **both** are true:

- `omnia.paypal.environment=live`
- the credentials file contains `"environment": "live"`

Every other combination leaves PayPal **not ready**: nothing is sent to PayPal, new checkouts are
refused, and `/api/paypal/config` answers `enabled:false` (reported as Sandbox).

| Setting | Credentials `environment` | Result |
|---|---|---|
| `sandbox` (or unset) | missing or `sandbox` | Sandbox (today's files keep working) |
| `sandbox` | `live` | refused |
| `live` | `live` | **Live** |
| `live` | missing or `sandbox` | refused |
| anything else (`LIVE`, `production`, empty) | any | refused |

Live sends to `api-m.paypal.com`, accepts approval links only on `www.paypal.com` and shows the
brand name "Omnia". Sandbox keeps `api-m.sandbox.paypal.com`, `www.sandbox.paypal.com` and
"Omnia Sandbox". The Sandbox credentials file never moves to Live: create a new file for the Live
app (owner-only permissions, outside the repository).

## App contract

`GET /api/paypal/config`

```json
{"enabled": true, "sandbox": false, "environment": "live", "currency": "EUR", "cardsEnabled": false}
```

`environment` is `live` only when Live is configured and ready. Checkout answers (`View`) carry the
checkout's **own** environment in `sandbox` and `environment`. Android (e47ca41) uses PayPal only
when they match the build: debug and staging builds need Sandbox, release builds need
`{"sandbox":false,"environment":"live"}`.

## One environment per checkout

`paypal_checkouts.environment` (V39) records where each checkout was created. V39 marks every
existing row `sandbox`, and the column accepts only `sandbox` or `live`. A checkout is sent only to
its own environment:

- **Check or capture:** refused before any request (`IllegalStateException`). The attempt marker is
  not set, so a checkout that never reached PayPal keeps its proof of that.
- **Buyer cancellation:** with a PayPal order ID, it is refused. Without one, if no create was ever
  attempted, the orders can still be released without asking any PayPal.
- **Repeating a create request with the same key:** refused.
- **Webhooks:** are matched only to checkouts of the server's environment. A Live event never
  reads, changes or records anything on a Sandbox checkout.
- **Recovery (SUPER_ADMIN):** evidence is `OTHER_ENVIRONMENT`, PayPal is not asked, and no
  resolution is permitted. Shipped orders can still be flagged for manual handling, as today.
- **Buyer's open checkout:** `pending` lists only the server's environment. An open Sandbox
  checkout neither blocks nor is offered for resuming on a Live server.

## Order #31 after the switch

Order #31 is a legacy Sandbox checkout in `RECOVERY_REQUIRED`, with stored PayPal order ID
`6TW30283Y86157032`, which PayPal Sandbox answers with 404 for the current credentials. This change
leaves it exactly as it is: V39 only adds `environment='sandbox'`.

On the Live server:

- **Recovery detail:** shows `OTHER_ENVIRONMENT`, sends no Live request and permits no action, as
  the 404 permits none today. Shipped orders could still be flagged.
- **Webhooks and buyer lists:** Live webhooks never touch it. It stays out of the buyer's pending
  list (`RECOVERY_REQUIRED` already is), and its orders and reserved stock are unchanged.
- **App:** the release app shows its PayPal details as not confirmed, because it belongs to
  Sandbox. Debug and staging builds can still read it.
- **Settling it later:** only with Sandbox evidence (a Sandbox-configured backend that has the
  credentials of the Sandbox app that created it). No real money can be involved, because it was a
  Sandbox order. Any other closure needs a separate, audited change; it is not part of this one.

## Switch checklist (production)

1. **Deploy with the environment unset.** Deploy this backend with `omnia.paypal.environment`
   unset, so it stays on Sandbox. V39 runs, and behaviour is unchanged apart from the new
   `environment` fields.
2. **Check production before switching.** Every Sandbox checkout other than order #31 must be
   `PAID`, `ABANDONED` or `REFUNDED`, with no open review case:
   ```sql
   SELECT id, state FROM paypal_checkouts
   WHERE state NOT IN ('PAID','ABANDONED','REFUNDED');
   ```
   After the switch, any other open Sandbox checkout needs a Sandbox-configured backend to finish.
3. **Prepare the Live app.** In PayPal Live, create the app and the webhook
   `https://api.myomniaapp.com/api/paypal/webhook`. Subscribe to:
   - `CHECKOUT.ORDER.APPROVED`
   - `PAYMENT.CAPTURE.COMPLETED`, `PAYMENT.CAPTURE.PENDING`, `PAYMENT.CAPTURE.DENIED`
   - `PAYMENT.CAPTURE.REFUNDED`, `PAYMENT.CAPTURE.REVERSED`

   Write the Live credentials file with `"environment":"live"`.
4. **Back up.** Take a database backup and an off-site copy.
5. **Switch.** Set `omnia.paypal.environment=live`, point `omnia.paypal.credentials-file` at the
   Live file, and restart. Check that `GET /api/paypal/config` answers `"environment":"live"`.
6. **Controlled first order.** Place a small order with your own PayPal account and confirm:
   - exactly one capture request
   - webhooks answered with 200
   - the order paid
   - the merchant and amount in the PayPal Live dashboard

   Then refund it in PayPal and confirm the refund reaches the server.
7. **Rollback.** Set the environment back to `sandbox` (or `enabled=false`) and restart. Live
   checkouts then wait untouched (refused, never sent to Sandbox) until Live is configured again.
