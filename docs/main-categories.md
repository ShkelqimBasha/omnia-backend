# Main categories

Omnia has 5 main categories plus Elektronike. V40 (`V40__add_main_categories.sql`) sets them up.
It matches categories by name, so it works on any database. It does not delete categories or move products.

| Category | Kind | Buyer actions | Listing fields |
|---|---|---|---|
| Veshje (was "Modë", same id) | CLOTHING | cart + payment | size, colour, brand, condition |
| Atlete & Këpucë (new) | SHOES | cart + payment | shoe size, brand, colour, condition |
| Kujdes për Lëkurën (was "Bukuri dhe Kujdes", same id) | SKINCARE | cart + payment | brand, product type, skin type, volume (ml), ingredients |
| Makina (new) | CAR | "Kontakto shitësin"; "Bli online" only if the seller chose "Kontakt + blerje online" **and** the flag is on | make, model, year, km, fuel, gearbox, location (+ price) |
| Prona (new) | PROPERTY | "Kontakto", "Kërko vizitë"; never cart or online payment | sale/rent, type, location, area m², rooms (+ price) |
| Elektronike (unchanged) | GENERAL | cart + payment | none (brand as before) |

These categories are set to INACTIVE: Shtëpi dhe Kuzhinë, Sport dhe Fitness, Libra dhe Kancelari, Lodra dhe Fëmijë and Ushqime dhe Pije.
They are hidden from menus, their ids and rows stay, and their products are untouched.
New listings in an INACTIVE category are refused.

## API

- `GET /api/categories` adds, per category:
  - `kind` and `sortOrder`
  - `fields[]` with key, Albanian label, type, required, options, unit, min/max and filterable
  - `cartEnabled`, `contactEnabled`, `visitRequestEnabled` and `onlinePurchaseOption`
- `POST`/`PUT /api/products` accept:
  - `attributes`: a map of field key to value. These are validated server-side: unknown keys, bad options, out-of-range numbers and missing required fields all give 400.
  - `saleMode`: cars only, either `CONTACT_ONLY` (the default) or `CONTACT_AND_BUY`.
  - A `brand` attribute is stored in the `brand` column.
  - For cars, the make is also used as the brand.
  - Older clients that send no attributes keep the stored ones on update.
- `GET /api/products` adds `categoryKind`, `attributes`, `attributeDetails[]` (label and display value), `saleMode`, `purchasable`, `contactEnabled`, `visitRequestEnabled`, `sellerPhone` and `sellerEmail`. The seller contact is only set on car and property listings.
- Filters use `attr.<key>=value`:
  - Selects need an exact match.
  - Text matches when it contains the value.
  - Numbers use `attr.<key>.min` and `attr.<key>.max`.
  - Keys come from a whitelist.
- `POST /api/products/{id}/inquiries` creates a request:
  - Body: `{type: CONTACT|VISIT, name, phone, message?, preferredDate?}`.
  - A visit can only be requested on a property, for a date within 90 days.
  - Each buyer can send at most 5 requests per listing per day.
  - `GET /api/inquiries/mine` lists the buyer's own requests.
  - `GET /api/organizations/{id}/inquiries` lists the requests for a seller's members.
- Server-side guards:
  - The cart and checkout refuse property, contact-only cars and every car while the flag is off.
  - A car is bought alone, quantity 1, for the full price. There is no deposit or reservation.

## Car "Bli online" flag

`app.catalog.car-online-purchase.enabled` (env `OMNIA_CAR_ONLINE_PURCHASE_ENABLED`) defaults to **false**.
While it is off:
- sellers cannot choose `CONTACT_AND_BUY`;
- car listings are never `purchasable`.

### PayPal checks before turning it on

These come from the official pages for Albania, read on 2026-11-23.

1. **Blocker: marketplace pre-approval.** The Acceptable Use Policy lists "Marketplaces" (item 18) among the activities that require PayPal's pre-approval (https://www.paypal.com/al/legalhub/paypal/acceptableuse-full).
   - Omnia takes every order to one platform merchant on behalf of many sellers (`PayPalGateway`, `payee.merchant_id`).
   - This applies to all categories, not only cars.
2. **Buyer Protection.** PayPal Buyer Protection excludes motor vehicles and real estate (https://www.paypal.com/al/legalhub/paypal/buyer-protection). The app tells the buyer this before payment.
3. **Not prohibited.** Vehicles are neither prohibited nor in the AL pre-approval chart.
4. **Limits.** The AL User Agreement sets no fixed per-payment cap. PayPal sets sending, receiving and withdrawal limits per account, and may place holds or reserves on high-risk activity. Check Omnia's own account limits for car-sized amounts (10k–50k €) with PayPal first.
5. **Ownership transfer.** Payment and transfer of ownership are separate steps. Ownership passes at the notary, after the payment, and the app says so.
