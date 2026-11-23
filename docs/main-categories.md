# Main categories

Omnia has 5 main categories plus Elektronike. V40 (`V40__add_main_categories.sql`) sets them up.
It matches categories by name, so it works on any database. It does not delete categories or move products.

| Category | Kind | Buyer actions | Listing fields |
|---|---|---|---|
| Veshje (was "Modë", same id) | CLOTHING | cart + payment; buyer picks a size | sizes with own stock (see below); colour, brand, condition |
| Atlete & Këpucë (new) | SHOES | cart + payment; buyer picks a size | sizes with own stock (see below); brand, colour, condition |
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

## Sizes (clothing and shoes)

V42 (`V42__add_product_sizes.sql`) adds sizes. Each size is a `product_variants` row named `SIZE` with its own stock.
The product's `stock` is always the sum of its sizes. Rules are in `catalog/ProductSizes` and `catalog/SizeStock`.

- Values:
  - Clothing: `XS, S, M, L, XL, XXL, XXXL, ONE_SIZE`, shown as XS…XXL, `3XL`, `Një masë`.
  - Shoes: 15–50 in half sizes, stored as `"38"`, `"42.5"`.
  - Lower case, `3XL`, `42,5` and `42.0` are accepted and stored in the form above.
- `POST`/`PUT /api/products` accept `sizes: [{value, stock}]`:
  - Clothing and shoes only. Values must be valid and not repeated, stock 0–1 000 000, at most 40 sizes.
  - `stock` is only needed for a product without sizes. With sizes, the server sets it to the sum.
  - On update the list replaces the current sizes. A size that stays keeps its id.
  - A removed size is deleted. If an open cart line uses it, it stays with stock 0 instead.
  - `sizes: []` makes the product unsized again (`stock` is then required). Its cart lines with a size are removed.
  - `sizes` left out on update keeps the sizes as they are.
  - Moving the product to a category without sizes drops them.
  - Moving between clothing and shoes needs the new `sizes` in the same request.
- `GET /api/products` adds `sizes: [{id, value, label, stock}]` in display order and `sizeRequired`.
  For other kinds, and clothing/shoe listings without sizes, these are `[]` and `false`.
- Cart (`POST /api/cart`): `variantId` (a size id) is required for a product with sizes and refused otherwise.
  The quantity, counting what is already in that line, may not go over that size's stock.
  Each size is its own cart line. `CartItemResponse` adds `sizeLabel` and `availableStock`.
- Checkout (`POST /api/orders/checkout`, PayPal checkout, `POST /api/orders`): each item carries `variantId` under the same rule.
  Stock is checked and taken per size under the product row lock. The order line stores `variant_id` and `variant_info` (`"Masa: M"`, `"Numri: 42.5"`).
  `OrderItemResponse` shows `variantId` and `variantInfo` in buyer, seller and admin views.
- Cancel, admin cancel and PayPal group cancellation put stock back on the line's size.
  If that size was deleted, a product with a single size gets it back. With several sizes nothing is put back, since there is no way to know which.
- The old `/api/product-variants` endpoints follow the same rules: name `SIZE` only, no price adjustment.
  Clients should send `sizes` on the product instead.
- The old single-value fields `size` (clothing) and `shoeSize` (shoes) are gone.
  V42 moved each stored value into one SIZE row holding the product's whole stock, and removed the keys.
  Shoe numbers between half sizes were rounded to the nearest half. `attr.size` and `attr.shoeSize` filters now give 400.

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
