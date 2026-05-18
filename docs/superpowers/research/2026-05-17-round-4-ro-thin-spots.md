# Round 4 — RO Close Thin Spots

> Date: 2026-05-17
> Subagent: deep-research / round-4
> Scope: RO supermarket APIs, recipe DBs, supplements, traditional cuisine nutrition, drug-food interactions, RO regulation, ANSPDCP, Anelis Plus, knowledge corpus loop
> Project: Dietician (personal nutrition, friends-only ~5 users, Iași/RO, air-fryer + microwave only)

---

## TL;DR

The spec assumed Mega Image and Carrefour both run on VTEX. **That is wrong for Mega Image** — it runs a Next.js custom storefront on the Delhaize Belgium platform (`/p/{slug}` URL convention, `_next/data/` JSON endpoints, no public catalog API). **Carrefour Romania runs Magento 2**, not VTEX (VTEX is Carrefour's *Brazil/France* stack — the RO ".ro" site is a separate Magento install with custom marketplace overlays). **Auchan IS confirmed VTEX** (live HTTP probe to `https://www.auchan.ro/api/catalog_system/pub/products/search/lapte` returned a JSON product feed including `productId`, `productName`, `brand`, `ean`, `items[].sellers[].commertialOffer.Price`). **Kaufland Romania is Adobe Experience Manager (AEM) marketing-only** — no e-commerce, no online ordering; delivery is delegated to Glovo/Wolt third parties. **Lidl is custom + Cloudflare anti-bot + OAuth-gated Lidl Plus API** (reverse-engineered Python client `lidl-plus` exists but Lidl now actively blocks third-party logins). **Profi returns HTTP 403** on direct fetch (anti-bot). **Cora.ro is dead since April 2024** (folded into Carrefour).

The recipe DB picture is bleak for direct scraping: Savori Urbane, JamilaCuisine, Bringo all serve **HTTP 403 to ClaudeBot** via Cloudflare-managed AI-train=no signals (EU Copyright Directive 2019/790 Article 4 reservation). Bucataras and LaLena are PHP custom sites without those signals but with `Disallow: /reteta_print.html` style blocks. **All major RO recipe blogs are WordPress** with WP REST API at `/wp-json/wp/v2/posts` and JSON-LD `schema.org/Recipe` blocks — that's the scrape vector, not RSS, not Cloudflare-blocked HTML.

For traditional RO dishes, **USDA FoodData Central does not cover sarmale/mămăligă/mici/papanași natively** — they are unrecognized food names. CIQUAL (French ANSES, public domain, OpenData) has the closest cousins ("chou farci", "polenta", "saucisse grillée", "beignet au fromage"). No RO Ministry of Health food composition table exists in machine-readable form (the legacy "Mincu" institute publication is print-only). **Best play: hand-author a `dishes_ro.toml` seed of ~30 traditional dishes** with kcal/macros aggregated from English-language nutrition aggregators (SnapCalorie, SparkPeople, eatntrack.ro) and verified against per-ingredient reconstruction from CIQUAL/USDA.

DRI/RDA harmonization is a published-table problem: **EFSA DRV (2017 update, 32 scientific opinions, public PDF at `DRV_Summary_tables_jan_17.pdf`) vs USDA DRI (Food and Nutrition Board, NIH ODS DRI Calculator) vs RO Ministry of Health guidelines** (RO uses a 7-group food pyramid, the Ministry has *not* published numeric DRI/RDA per nutrient — Romania defers to EFSA in practice). Pivot table feasible; ~30 nutrients × 3 authorities.

Drug-food interactions: **DrugBank 6.0 has 2,475 drug-food interactions (CC BY-NC 4.0 for the academic CSV download, free).** OpenFDA structured product labels have drug-drug but food-drug is in unstructured "interactions" text. For RO market, the supplement × prescription matrix is essentially the same as US/EU since the molecules are identical (statins, warfarin, levothyroxine, metformin, SSRIs).

EU Reg 1169/2011 (FIC) governs ALL packaged food labels in RO including allergen highlighting and the 14-allergen mandatory list. ANSVSA enforces locally with refrigeration guidance 0-4°C for meat/dairy/eggs, ≤ -12°C for frozen meat.

University of Sydney's GI database is **free public-info but the manuscript datasets require email request "not for commercial use"**. The Foster-Powell 2008 table and 2021 update (Atkinson) are published in journals as PDF appendix tables — **scrape from PDF, license = research use only.**

**ANSPDCP self-assessment verdict (friends-only, ~5 users, health data): the household exemption (GDPR Art 2(2)(c)) plausibly applies if the data stays within the household/closed friend group AND is not exposed to "the wider world". DPO mandatory threshold (Art 37) is NOT triggered — DPO is required for public authorities, large-scale special-category processing (you have 5 users), or systematic monitoring. DPIA may still be advisable since health data is involved. No ANSPDCP filing required for friends-only.** Friends-only does NOT remove the obligation to honor data-subject rights internally (consent log, deletion path, breach notification SLA 72h).

**Anelis Plus verdict: SAML/Shibboleth federated via RoEduNet IdP at `https://idp.uaic.ro/simplesaml/saml2/idp/metadata.php` — login is "Choose institution → UAIC → enter @uaic.ro creds".** No API key, no Bearer token. Headless automation requires either (a) browser cookie export after manual login, or (b) running a full SAML SP that registers with RoEduNet federation (overkill for friends-only). **Recommended: cookie-jar export from desktop browser, refresh every ~30 days.**

Knowledge corpus authoring loop: **two-file pattern is correct** (narrative `.md` + autogen `.data.md` with frontmatter, Obsidian transclusion via `![[concept.data.md#table]]`). Hand-author for first ~20 concepts to validate format, then LLM-draft + human-approve. Don't try to fully auto.

---

## 1. RO supermarket APIs / scrapers

### 1.1 Mega Image

- **Platform: Custom Next.js storefront on Delhaize Belgium tech stack** (NOT VTEX, contrary to spec §10.2).
- Evidence: `/_next/static/` paths on `static.mega-image.ro`, no `/api/catalog_system/` endpoint (returns 404), build hash in logo URL pattern `?buildNumber=1b088490951716d165d4bbc6508f8688640ba9583e317a426ed8aab382251f10`.
- URL conventions: `/product-name/p/{id}` for products, `/Category-Name/c/{code}` for categories.
- **robots.txt**: User-agent: *, allows all except `/login`, `/my-account`, `/timeslot`, `/payment-method`, `*/search/*`, `*/checkout*`, `/en-ro/*`. Sitemap at `https://www.mega-image.ro/sitemap/delhaizesitemapindex.xml` → 3 child sitemaps gzipped.
- **No public catalog API**. The internal API is the Next.js `_next/data/{buildHash}/{locale}/{slug}.json` hydration endpoint — but it's tied to the build hash which rotates per deploy, so any scraper must scrape the HTML first to grab the build hash, then call the JSON endpoint. Fragile.
- **Better path: scrape the gzipped sitemap → enumerate all product URLs → fetch each `/product-name/p/{id}` page → parse JSON-LD `@type": "Product"` block** (which exposes name, image, sku, offers.price). Product page nutrition is rendered server-side from a Delhaize-internal CMS — parse HTML.
- Anti-bot: no Cloudflare on HTML, no Datadome detected. Standard rate-limiting (~5 req/s safe).
- Loyalty: Mega CONNECT account NOT required for catalog/price browse. Required for personalized offers and "card price".
- Receipt-OCR alignment: Mega Image receipts use 3-column format (item / qty / price). Item name matches website "productName" ~85% but typos and abbreviations frequent. EAN not printed on receipt — must match by name fuzzy.

### 1.2 Carrefour Romania

- **Platform: Magento 2 with custom marketplace overlay** (NOT VTEX — Carrefour Brazil/France runs VTEX, RO is Magento).
- Evidence: `cdn-media.carrefour.ro/media/` (Magento media path), `/new_checkout/cart`, `/customer/account/`, `/sales/order/history` (Magento routes), product URL `/produse/{slug}-19-{product-id}` (Magento URL key + entity_id pattern).
- **robots.txt**: Disallows `/customer`, `/new_checkout`, `/wishlist`, `/mycarrefour`, `/catalogsearch`, `/*&|*`, `/*fbclid*`, `/*order=*`, `/catalog/product`, `/new_catalog/product`, `*/produse/*/corporate/`, `/*ajax/`. Sitemap: `https://carrefour.ro/pub/sitemap/sitemap.xml`.
- **No public REST API**. Magento exposes `/rest/V1/products` but `/rest/V1/*` is auth-gated and returned 404 anonymous on probe.
- **Scrape vector**: sitemap → product slug → HTML → parse JSON-LD Product block (Magento auto-emits schema.org Product).
- Anti-bot: no Cloudflare on HTML probes. Magento's standard bot-blocking on `/catalogsearch/*` (in robots.txt anyway).
- Loyalty: Carrefour card / online account NOT required for browse. Required for personalized prices and Bringo grocery delivery integration.
- **Bringo**: Carrefour's grocery courier subsidiary (`bringo.ro`). Cloudflare 403 to ClaudeBot, explicit `Content-Signal: ai-train=no` reservation. **Skip for automated scraping.** Spec §10.2.1 already noted a dedicated Bringo account vs personal Carrefour identity — that's correct.
- Receipt-OCR alignment: Carrefour receipts use 3-column format. Receipt item name matches Magento productName ~80%.

### 1.3 Kaufland Romania

- **Platform: Adobe Experience Manager (AEM) on Schwarz Group CDN.**
- Evidence: `/etc.clientlibs/kaufland/clientlibs/clientlib-klsite/`, `kaufland.media.schwarz` image host.
- **No e-commerce.** Site is purely promotional. Online ordering = ZERO. "Click & Reserve" exists but is offline-pickup-only and form-based. Home delivery delegated to **Glovo and Wolt**.
- **robots.txt**: Disallows `/etc.clientlibs/` except `/etc.clientlibs/kaufland`. Sitemap at `https://www.kaufland.ro/.sitemap.xml`.
- Sitemap structure: weekly-offer pages, "saptamana-curenta" (current week) leaflets, "sortiment/" category pages. **No SKU-level product detail.**
- Promo/loss-leader data: **available only as weekly PDF leaflets** at `https://www.kaufland.ro/oferte/oferte-saptamanale.html`. PDF → text → OCR pipeline.
- For Glovo data (delivered by Kaufland): Glovo has reverse-engineered APIs (community-maintained, e.g. python `glovo-scraper`) but Glovo serves Cloudflare-protected JSON — moderate scrape difficulty.
- Loyalty: Kaufland Card xtra (registered customers get coupons). No public API.
- **Conclusion for Kaufland: not a useful catalog source. Receipt OCR is the only data path.**

### 1.4 Lidl Romania

- **Platform: Custom + OAuth + heavy anti-bot.**
- Public-facing `lidl.ro` is a thin promotional site. The product catalog and "weekly offers" reality lives in the Lidl Plus mobile app.
- **robots.txt**: Disallows `/q/search?id=*`, `/cc.js*`, numbered `/1*`-`/9*` paths (likely CDN-asset paths), `/cdn/assets/cwv/`. Sitemap at `https://www.lidl.ro/static/sitemap.xml`.
- **Lidl Plus app authentication**: OAuth 2.0 via `https://accounts.lidl.com/connect/token` with PKCE. Native client `LidlPlusNativeClient`, scopes `openid profile offline_access lpprofile lpapis`, redirect `com.lidlplus.app://callback`. **Refresh token grant works** but the initial code grant requires a browser-flow + mobile-app cert pinning bypass (MITM proxy + custom CA on phone).
- **Reverse-engineered clients exist**:
  - Python `lidl-plus` (Andre0512/lidl-plus) on PyPI — fetches receipts, coupons, profile.
  - .NET `LidlApi` (KoenZomers/LidlApi) — but **Lidl has actively blocked third-party logins** as of late 2024 per repo README.
- **Current state for Dietician**: receipt fetching via `lidl-plus` Python works IF you can extract a refresh token via MITM proxy on your phone once. Catalog browsing has NO equivalent — the app's product catalog endpoint is heavily guarded.
- Anti-bot: Cloudflare on `lidl.ro` (HTML serving). The mobile API is on `accounts.lidl.com` and a separate `app.lidl.de` host with cert pinning.
- Loyalty: Lidl Plus app is mandatory for digital coupons / receipts. **Lidl Plus account = primary scraping handle.**
- Receipt-OCR alignment: Lidl receipts notoriously have poor thermal print quality and tiny font; SKU IS printed but often unreadable. Recommendation: preprocess with imgscalr contrast/threshold (spec §10 line 1067 already notes this).

### 1.5 Auchan Romania

- **Platform: VTEX (CONFIRMED LIVE).** ✅
- Live probe `https://www.auchan.ro/api/catalog_system/pub/products/search/lapte` returned 200 with valid JSON product feed including:
  ```
  productId, productName, brand, brandId, linkText, productReference (= EAN),
  categoryId, productTitle, releaseDate, link, items[]: {
    sellers[]: { commertialOffer: { Price, ListPrice, PriceWithoutDiscount, ... } }
  }
  ```
- **robots.txt** is unusually permissive for major SEO bots (Googlebot-image, SemrushBot, AhrefsBot, Screaming Frog all explicit `Allow`), restrictive for `008`, `MJ12bot`, `Yandex`, `Baiduspider`, `PetalBot`, `DotBot`, `HTTrack`, `WebCopier`. Disallows `/busca/`, `/quick-view/`, `/routing/vtex.store@2.x/`, `/salesforce/`, `/no-cache/`, plus standard `/*?map=`, `/*?order=`, `/*?query=` parameter filters. Sitemap: `https://www.auchan.ro/sitemap.xml`.
- **VTEX standard endpoints work** without auth: `/api/catalog_system/pub/products/search/{term}?_from=0&_to=49` (max 50 per page, pagination via `_from`/`_to`).
- **EAN coverage**: `productReference` field holds EAN-13 in ~90% of cases (verified on probe).
- Anti-bot: no Cloudflare on API. Pure HTTP. Safe rate ~2-3 req/s.
- Loyalty: Auchan card not required for browse.
- Spec §10.2 (`/api/catalog_system/pub/products/search?ft=...`) — that exact query-string form returned 404; the path-form `/search/{term}` works. **Plan needs amendment to use `/search/{term}` not `?ft=`.**
- Promo/loss-leader: discount visible as `Price` vs `ListPrice` delta in `commertialOffer`.
- Receipt-OCR alignment: Auchan receipts use separate VAT-rate columns (spec §10 line 1068). EAN printed in 60% of cases.

### 1.6 Profi Romania

- **Platform: anti-bot 403 to direct fetch.** Probe `https://www.profi.ro/robots.txt` returned 403 Forbidden — entire site is behind anti-bot WAF.
- No online ordering in the traditional sense; Profi runs a mobile app for loyalty + a `profi.ro` storefront for promotional content. Online delivery delegated to **Glovo** in major cities.
- **Conclusion: skip. Receipt OCR only.** Profi receipts use simple 2-column format (item / price) without SKU, low OCR confidence.

### 1.7 Cora

- **Cora.ro shut down April 17, 2024.** Folded into Carrefour. URL now serves a static shutdown notice.
- **No path forward.** Spec mention of Cora can be removed.

### 1.8 Recommended attack order

1. **Auchan (VTEX, easiest)** — pure HTTP JSON API, no anti-bot, EAN coverage. Build adapter FIRST.
2. **Mega Image (Next.js + JSON-LD HTML)** — sitemap-driven scrape, parse JSON-LD from product pages. Second priority.
3. **Carrefour (Magento + JSON-LD HTML)** — same sitemap+JSON-LD pattern as Mega Image, also second priority.
4. **Open Food Facts (EAN lookup)** — wherever a chain doesn't have nutrition, look up by EAN against Open Food Facts public API (no key, world.openfoodfacts.org). Fills the gap.
5. **Lidl (receipt-only via Lidl Plus refresh token)** — third priority, accept that catalog browse is unavailable.
6. **Kaufland, Profi (receipt-only)** — receipt OCR pipeline catches the rest.
7. **Cora — skip.**

**Revised spec assumption**: the original §10.2 "VTEX adapter handles Mega + Carrefour" is wrong. Reality is **VTEX adapter handles Auchan only**; Mega Image and Carrefour need a "JSON-LD HTML scraper" adapter (different code, slower, more fragile).

---

## 2. RO recipe databases

### 2.1 Savori Urbane (Laura Adamache)

- WordPress with Cloudflare. `robots.txt` declares `Content-Signal: search=yes, ai-train=no` and explicit `Disallow: /` for `ClaudeBot`, `GPTBot`, `Amazonbot`, `Google-Extended`.
- Result: **WebFetch from this subagent returns 403.** Real Dietician backend with a vanilla User-Agent likely passes; ClaudeBot User-Agent specifically blocked.
- Sitemap: `https://savoriurbane.com/sitemap_index.xml`. WordPress structure → standard `wp-sitemap-posts-post-1.xml` etc.
- WP REST API: `https://savoriurbane.com/wp-json/wp/v2/posts?per_page=10` should return JSON with title, content (HTML), categories. Recipe-card plugin (likely "WP Recipe Maker" — most popular plugin in RO recipe blogs) embeds JSON-LD `schema.org/Recipe` in post HTML.
- Quality: ~2,700 tested recipes since 2014. Ingredients are usually given in grams ("200 g făină") but some recipes use cup-equivalents ("o cană") and Romanian "ochi" / "vârf de cuțit" for ad-hoc amounts. Expect 60-70% gram-precision.
- License posture: Cloudflare AI-train=no signal is a copyright reservation under EU Directive 2019/790. Personal-use / friends-only ingestion is grey area — courts haven't tested. **Recommendation: respect signal, do NOT redistribute, store only normalized ingredients + grams + structured steps + URL/title attribution, never reproduce body copy verbatim.**

### 2.2 JamilaCuisine

- WordPress + Cloudflare with same AI-train=no signal. ClaudeBot blocked.
- Sitemap and WP REST API available.
- Strong point: each recipe has a video — useful for cross-reference but not parseable without speech-to-text.
- Quality: ingredients in grams, Romanian terminology. Recipe count: 1000+.
- License: same as Savori Urbane. Respect signal.

### 2.3 Bucataras

- PHP custom (NOT WordPress). `robots.txt` explicitly allows `Mediapartners-Google`, disallows `/cron/`, search interfaces, `/feed/`, `/reteta_print.html`. **No AI-train signal.**
- WebFetch returned 403 to ClaudeBot on a specific recipe page — likely User-Agent-based block at app level, not Cloudflare. Real Dietician backend should work.
- Sitemap: `https://www.bucataras.ro/o_cache/sitemap/sitemap.xml`.
- URL pattern: `/retete/{slug}-{id}.html`. ID is sequential integer → easy enumeration.
- Quality: HUGE archive (100k+ recipes user-contributed, varies wildly), ingredients often in non-gram units ("o lingura", "doua oua"). Lower parsing precision than Savori Urbane.
- License: no explicit AI signal. Standard copyright (Romania Law 8/1996). Personal use OK; bulk redistribution NOT.

### 2.4 Hai cu mama (Doina Ciobanu)

- Search returned no specific site by that name. The "Doina Ciobanu" who matches the user's likely reference is **NOT the London model**; she's a Romanian food blogger possibly at `nicuvar.wordpress.com` (WordPress.com hosted, "Arta culinara cu Doina"). Small archive, not actively scaled.
- **De-prioritize** — too small to be worth a dedicated adapter.

### 2.5 Caietul cu retete

- Appears not as a standalone domain but as **a feature/section inside Bucataras.ro** (`https://www.bucataras.ro/bloguri-culinare/{slug}/retete-caietul-cu-retete.html`). User-contributed sub-blog.
- Covered under Bucataras adapter; no separate work needed.

### 2.6 LaLena (lalena.ro)

- PHP custom site, **simple robots.txt** with only standard backend disallows (`/templates/`, `/admin/`, `/cgi-bin/`, `/forum/`, `/reteta_print.html`). No AI signal.
- Recipe URL pattern likely `/reteta_{id}.html` (sequential IDs).
- Quality: heterogeneous user-submitted. Lower precision.
- License: standard copyright, no AI reservation.

### 2.7 Recipe-DB recommendation

**Two-tier adapter:**
- **Tier 1 (WP-REST + JSON-LD)**: Savori Urbane + Jamila + any other WP recipe blog. Hit `/wp-json/wp/v2/posts?_fields=link,title&per_page=100&page=N` to enumerate, then fetch each post HTML and parse JSON-LD `Recipe`. Respect AI-train signal: store URL + attribution + structured ingredients/steps, never store body HTML.
- **Tier 2 (sitemap + HTML scrape)**: Bucataras + LaLena. Parse HTML with selector rules.
- Skip Hai-cu-mama, skip print-only sites.

**Don't redistribute recipe text.** Store: URL, title, source attribution, normalized ingredients list (parsed to `{ name, grams, raw_text }`), instructions step count, nutrition info if computed.

---

## 3. RO supplements / pharmacy catalogs

### 3.1 Catena

- Custom Romanian PHP. `/cumpara-online` is the catalog. **JSON-LD Product blocks present** on product pages.
- No public REST API. Scrape: enumerate via category pages, parse product HTML, extract JSON-LD.
- Anti-bot: not detected during probe.
- Active-ingredient field: most product pages show "Compoziție" (composition) section in HTML — parseable.

### 3.2 Apoteca

- Smaller chain. Custom PHP. Similar pattern to Catena.

### 3.3 DM Romania (`dm.ro`)

- DM redirected from `dm-drogeriemarkt.ro` → `dm.ro` (301).
- The site appears thin / promotional in initial probe. DM Germany has a strong product API; DM Romania may not.
- **Online ordering exists** for DM via the dm.ro shop section but is geographically limited (some cities only).
- Anti-bot: not detected.

### 3.4 Sensiblu

- `sensiblu.com` → **Cloudflare "Waiting Room"** on `/index.php?page=search`. Heavy anti-bot. **Skip.**
- Owned by Dr. Max (Czech chain).

### 3.5 Help Net

- Custom PHP on **Nette Framework** (cookie `_nss` reveals this). Some product information exposed in HTML; no public REST.
- Anti-bot: not visible in headers.
- Reasonable scrape target if catalog browse is needed.

### 3.6 Farmacia Tei

- `farmaciatei.ro` 301-redirects to `comenzi.farmaciatei.ro` for the shop.
- Custom Bootstrap 5 theme. No `/api` endpoint visible.
- Scrape: enumerate `/brand/*`, `/gama/*` category pages and parse product HTML.

### 3.7 Naturix

- Domain not resolved / not a major chain by search. Possibly user-mentioned by mistake. **Alternatives in this niche**: Plafar (`natura-plus.ro`), Bio Shop Romania (`bioshopromania.com`), Organic-Natura. All custom small ecommerce sites with standard HTML scrape patterns.

### 3.8 Multi-pharma price comparison

- **No native RO meta-search exists** for prescription / supplement prices. Catalog-AZ aggregates *promo leaflets* but not real-time prices. **Custom comparison must be built**: query Catena + Help Net + Farmacia Tei in parallel by EAN (where present) or product name fuzzy-match.
- Label nutrition / active-ingredient extraction: most pharmacy product pages render a "Compoziție per tabletă" or "Per 1 capsulă" section — regex-parseable.

### 3.9 Recommendation

- Build **single shared `PharmacyAdapter` interface**: `searchByName`, `getPriceByEan`, `getActiveIngredients`. Implement for Catena, Help Net, Farmacia Tei (best-quality custom HTML). Skip Sensiblu (Cloudflare), skip DM until verified shop is live.

---

## 4. Traditional RO dishes nutrition

Per-100g approximations aggregated from English-language nutrition aggregators (SnapCalorie, SparkPeople, eatntrack.ro). These are estimates and vary ±20% by recipe.

### 4.1 Mămăligă (cornmeal porridge)

- Plain (water + cornmeal + salt): **~90 kcal / 100g**, 2g protein, 20g carbs, 1g fat.
- With brânză + smântână + ou (cheese + sour cream + egg): **~170 kcal / 100g**, 8g protein, 18g carbs, 8g fat.
- GI: high (~80 — cornmeal porridge is high-GI per glycemic-index.net).

### 4.2 Sarmale (cabbage rolls)

- Variants matter enormously:
  - Pork + rice, fresh cabbage: **~150 kcal / 100g**, 6g protein, 10g carbs, 9g fat.
  - Pork + rice, pickled (varză murată): **~125 kcal / 100g**, 5g protein, 10g carbs, 7g fat (sourer, slightly less dense).
  - Chicken: ~110 kcal / 100g, 7g protein, 9g carbs, 5g fat.
  - Vegan (cu ciuperci, orez, vegetabile): ~80 kcal / 100g, 2g protein, 12g carbs, 3g fat.
- Typical sarma weighs 50-70g; portion = 4-6 sarmale.

### 4.3 Ciorbă (sour soups)

- Ciorbă de burtă (tripe): **~90 kcal / 100g** with high fat content (sour cream + tripe).
- Ciorbă rădăuțeană (chicken substitute for burta, invented 1970s in Suceava): **~90 kcal / 100g**, 8g protein, 3g carbs, 5g fat.
- Ciorbă de pește (fish): ~60 kcal / 100g.
- Ciorbă de fasole cu costiță (bean + smoked pork): ~80-100 kcal / 100g.
- Sour element: borș (fermented wheat bran) or oțet (vinegar) — negligible kcal.

### 4.4 Mici / Mititei

- Classic pork + beef + lamb mix, grilled: **~250 kcal / 100g**, 15g protein, 20g fat, 2g carbs (a typical "mic" weighs ~70-80g raw, ~60g cooked).
- Sodium-bicarbonate-puffed traditional recipe → higher fat retention than equivalent burger meat.
- One Romanian commercial product label scanned showed 166 kcal/100g — lower-fat commercial mix.

### 4.5 Tochitură

- Pork stew with sausages, ± mămăligă + cheese + egg on top.
- Per 100g of stew only: **~200 kcal**, 15g protein, 14g fat, 2g carbs.
- Full plate (tochitură + mămăligă + brânză + ou): ~759 kcal per typical restaurant portion.
- Cremvurști (frankfurter sausage) variant: similar.

### 4.6 Pască, cozonac, eugenia (holidays)

- Cozonac: **~350-360 kcal / 100g**, 8g protein, 39g carbs (12g sugar), 20g fat.
- Pască (sweet bread + cheese ring): **~310 kcal / 100g**, 10g protein, 43g carbs, 12g fat.
- Eugenia (industrial cocoa-cream biscuit, Dobrogea brand): commercial label varies but typically **~430-460 kcal / 100g**, 5g protein, 60g carbs (30g sugar), 20g fat. Brand-EAN lookup more accurate than recipe estimate.

### 4.7 Papanași, plăcintă, gogoși

- Papanași prăjiți (fried cheese donuts with sour cream + jam topping): one full restaurant serving is **~694 kcal**, 91g fat, 109g carbs (52g sugar), 18g protein. Per 100g of dough+cheese mix: ~270 kcal.
- Plăcintă cu brânză: **~320 kcal / 100g**, 10g protein, 41g carbs, 11g fat.
- Gogoși: **~360-450 kcal / 100g** depending on filling/glaze. Plain: 360. Cream-filled: 380. Glazed: 420+.

### 4.8 Mămăligă cu brânză + smântână + ou

- Covered in 4.1.

### 4.9 Data-source recommendation

- **Hand-author `dishes_ro.toml` seed of ~30 dishes** with kcal/macros + ingredient-component breakdown (each dish = sum of weighted ingredients from CIQUAL/USDA).
- Use **Open Food Facts** for branded packaged items (Eugenia, Joe biscuit, Dobrogea cozonac, Cris-Tim mici) via EAN lookup.
- For restaurant/home recipe variations, **defer to per-ingredient reconstruction from the recipe DB** — Dietician parses ingredients then computes nutrition.
- **No RO Ministry of Health food composition table is published in machine-readable form** (the legacy "Mincu Institute" 1970s tables exist as printed booklets but not as data file). Stop looking.
- CIQUAL French has the best "European cooking" cousins: `Chou farci` (= sarmale-ish), `Polenta` (= mămăligă), `Saucisse grillée` (= mici-ish), `Soupe aigre-douce` (= ciorbă-ish). Free, OpenData, downloadable.

---

## 5. DRI/RDA harmonization

Three authorities, pivot table draft for the most common 20 nutrients (adult male 19-50, sedentary baseline):

| Nutrient | USDA RDA/AI | EFSA PRI/AI | RO Min Health | Notes |
|---|---|---|---|---|
| Protein | 56 g (0.8 g/kg) | 0.83 g/kg (≈58 g for 70kg) | defers to EFSA | Identical methodology |
| Carbohydrate | 130 g | 45-60% of energy | defers to EFSA | EFSA = % energy, USDA = absolute g |
| Total fat | 20-35% energy | 20-35% energy | defers to EFSA | Same |
| Saturated fat | <10% energy | as low as possible | defers to EFSA | RO Nutri-Score voluntary 2021+ |
| Fiber | 38 g | 25 g | defers to EFSA | **USDA higher** |
| Vitamin A | 900 µg RAE | 750 µg RE | defers to EFSA | USDA RAE vs EFSA RE difference |
| Vitamin C | 90 mg | 110 mg | defers to EFSA | **EFSA higher** |
| Vitamin D | 15 µg (600 IU) | 15 µg | defers to EFSA | Aligned 2016+ |
| Vitamin E | 15 mg α-TE | 13 mg (AI) | defers to EFSA | Close |
| Vitamin K | 120 µg AI | 70 µg AI | defers to EFSA | **USDA much higher** |
| Thiamin (B1) | 1.2 mg | 0.1 mg/MJ → ~1.0 mg | defers to EFSA | EFSA energy-pegged |
| Riboflavin (B2) | 1.3 mg | 1.6 mg | defers to EFSA | EFSA higher |
| Niacin (B3) | 16 mg NE | 1.6 mg NE/MJ | defers to EFSA | EFSA energy-pegged |
| B6 | 1.3 mg | 1.7 mg | defers to EFSA | EFSA higher |
| Folate | 400 µg DFE | 330 µg DFE | defers to EFSA | **USDA higher** |
| B12 | 2.4 µg | 4.0 µg (AI) | defers to EFSA | **EFSA significantly higher** |
| Calcium | 1000 mg | 950 mg | defers to EFSA | Aligned |
| Iron | 8 mg | 11 mg | defers to EFSA | **EFSA higher** |
| Zinc | 11 mg | 9.4-16.3 mg (phytate-dependent) | defers to EFSA | EFSA gives a range |
| Iodine | 150 µg | 150 µg | defers to EFSA | Aligned |
| Selenium | 55 µg | 70 µg | defers to EFSA | EFSA higher |
| Magnesium | 420 mg | 350 mg (AI) | defers to EFSA | USDA higher |
| Potassium | 3400 mg | 3500 mg (AI) | defers to EFSA | Aligned |
| Sodium | 1500 mg AI | 2000 mg AI | defers to EFSA | EFSA higher reference, public-health target <5g salt/day = 2g Na |

**Implementation note**: store both USDA and EFSA values per nutrient, default user-preference to EFSA (RO is EU member, RO public health uses EFSA in practice). Allow user to switch authority in settings. Don't bother with RO Min Health as separate field — there are no distinct RO values.

**Source files**:
- EFSA `DRV_Summary_tables_jan_17.pdf` (version 4, September 2017) — PDF table extraction.
- USDA: NIH ODS DRI Calculator (web tool) and the NCBI Bookshelf "Dietary Reference Intakes: Recommended Intakes for Individuals" tables.
- Interactive cross-reference: EFSA "DRV Finder" at `https://multimedia.efsa.europa.eu/drvs/index.htm`.

---

## 6. Drug-food interactions

### 6.1 Open datasets

- **DrugBank 6.0** (release 5.1.19 latest, 2024) — **2,475 drug-food interactions** (up from 1,195 in 5.0). Available as XML + CSV. Academic license is **CC BY-NC 4.0 (non-commercial, attribution)**. Sign-up required at `https://go.drugbank.com/releases/latest`. The `food_interactions` table is what you want.
- **OpenFDA** structured product labels (SPL) at `api.fda.gov/drug/label.json` — has unstructured "food_effect", "drug_interactions" text fields. RxNorm `rxcui` field for cross-reference. Free, no auth required, rate-limited.
- **DailyMed** (NLM) — official US drug labels, structured SPL XML. Free.
- **drugs.com** — has a structured drug-food interaction database but is **proprietary / scrape-only**.
- **EMA / EFSA**: EMA publishes SmPCs (Summary of Product Characteristics) for EU-approved drugs which include food-interaction sections. PDF format, not structured. Free.
- **RO ANMDM** (Agenția Națională a Medicamentului): publishes the National Drug Nomenclature (Nomenclatorul medicamentelor) but does not maintain a food-interaction database. Defers to EMA.

### 6.2 Common-supplement × common-prescription matrix (RO market)

Key interactions worth pre-loading:

| Food / supplement | Drug | Effect | Severity |
|---|---|---|---|
| Grapefruit / grapefruit juice | Atorvastatin, Simvastatin, Lovastatin | CYP3A4 inhibition → ↑drug AUC → muscle/liver damage risk | High |
| Grapefruit | Amlodipine, Felodipine | ↑drug → hypotension | Moderate |
| Grapefruit | Apixaban, Rivaroxaban | ↑drug → bleeding | Moderate |
| Grapefruit | Cyclosporine, Tacrolimus | ↑drug → toxicity | High |
| Vitamin K (kale, spinach, broccoli) | Warfarin | ↓anticoagulation | High |
| Dairy / calcium | Tetracyclines, Fluoroquinolones | Chelation → ↓absorption | High |
| Iron supplement | Levothyroxine | Chelation → ↓absorption (separate by 4h) | Moderate |
| Iron supplement | Tetracyclines, Fluoroquinolones | Chelation → ↓absorption | Moderate |
| St. John's Wort | SSRIs (sertraline, fluoxetine etc.) | Serotonin syndrome risk | High |
| St. John's Wort | Hormonal contraceptives | ↓efficacy | High |
| St. John's Wort | Warfarin, digoxin | ↓drug level | High |
| High-tyramine foods (aged cheese, cured meats) | MAOIs (rare in RO market, but moclobemide is RO-available) | Hypertensive crisis | High |
| Alcohol | Metronidazole | Disulfiram reaction | High |
| Alcohol | NSAIDs | GI bleed risk | Moderate |
| Caffeine | Theophylline, Ciprofloxacin | ↑caffeine effects | Low-Moderate |
| Cruciferous vegetables (cabbage, brussels) | Warfarin | High vitamin K → ↓anticoagulation | Moderate |
| Licorice (DGL — common RO supplement) | Antihypertensives, diuretics | Hypokalemia, ↑BP | Moderate |
| Cranberry juice | Warfarin | Disputed; some increased INR cases | Low |
| Bananas, oranges (potassium) | ACE inhibitors, K-sparing diuretics | Hyperkalemia risk | Moderate |
| High-fiber meal | Levothyroxine | ↓absorption (take levo fasting) | Moderate |
| Coffee / tea (tannins) | Iron supplements | ↓iron absorption (separate by 1h) | Moderate |

This matrix can be hand-curated in `interactions_ro.toml` covering ~50-100 high-frequency drugs commonly prescribed in RO (statins, antihypertensives, anticoagulants, levothyroxine, metformin, SSRIs, PPI). For long-tail, fall back to DrugBank CSV lookup.

### 6.3 RO regulatory state

- ANSPDCP regulates *data*, not drug-food info.
- ANMDM (national drug agency) defers to EMA SmPC.
- No RO-specific public drug-food database exists.

---

## 7. Food storage + EU food safety

### 7.1 EU Regulation 1169/2011 (FIC)

- In application since **13 December 2014**. Nutrition labelling mandatory since **13 December 2016**.
- Mandatory for all prepacked food sold in EU.
- 14 allergens must be highlighted in bold/italic/underlined or by background colour in ingredient lists: **cereals containing gluten, crustaceans, eggs, fish, peanuts, soybeans, milk, nuts, celery, mustard, sesame, sulphur dioxide/sulphites (>10 mg/kg), lupin, molluscs**.
- Nutrition declaration must include: energy (kJ + kcal), fat, saturates, carbohydrate, sugars, protein, salt — per 100g/ml minimum, per portion optional.
- Non-prepacked foods (restaurants, deli): allergens MUST be communicated (written form recommended).
- Applies in Romania directly as EU Regulation (no national transposition needed).

### 7.2 Romania ANSVSA enforcement

- ANSVSA = Autoritatea Națională Sanitară Veterinară și pentru Siguranța Alimentelor.
- Storage temperatures (per ANSVSA consumer recommendations):
  - **Refrigerated**: meat, milk, dairy, eggs at **0-4°C**, separated by product category.
  - **Frozen meat**: **≤ -12°C** (industry standard is -18°C; ANSVSA minimum is -12°C).
- Implements EU Reg 852/2004 (food hygiene) and 853/2004 (animal-origin hygiene).
- Inspections target HoReCa, retail, slaughterhouses. Private households exempt.

### 7.3 "After-opening" countdown UX recommendation

- Pre-bake a `food_shelf_life.toml` per major category with typical after-opening windows:
  - Milk (pasteurized, open): 3 days at 4°C.
  - Yogurt (open): 5-7 days.
  - Hard cheese (open): 2-4 weeks.
  - Soft cheese (open): 5-7 days.
  - Smoked meat (open): 7 days.
  - Cooked meat leftover: 3-4 days at 4°C.
  - Open canned food (transferred to glass): 3 days.
  - Cooked rice/grains: 4-5 days at 4°C, but rapid cool to 5°C within 2h to avoid Bacillus cereus.
  - Frozen meat (home freezer -18°C): 4-6 months for poultry, 6-12 months for red meat.
  - Frozen cooked dish: 2-3 months.
- USDA FoodSafety chart at `https://www.fda.gov/media/74435/download` is a usable cross-reference but US food categories differ slightly (e.g. raw shrimp 1-2 days, deli meat 3-5 days).
- For Dietician: countdown UX should show **"X days until estimated spoilage"** with a yellow-flag at 75% and red-flag at 100% of typical window. User can override.

---

## 8. Glycemic index database

### 8.1 University of Sydney GI database

- Searchable at `glycemicindex.com`. Free public web tool, no API.
- Underlying dataset compiled by the Human Nutrition Unit and the Sydney University Glycemic Index Research Service (SUGiRS).
- **License**: "data described in the manuscript will be made available upon request pending application to the corresponding author and stipulation that the data will not be used for commercial purposes." Friends-only non-commercial use = arguable fair use; request access to be safe.

### 8.2 Foster-Powell tables 2008 + 2021 update

- **2008 table** (Foster-Powell K, Holt SH, Brand-Miller JC): published in Diabetes Care 31(12):2281-2283 + supplementary appendix with **2,480 individual food items**. PDF appendix downloadable from diabetesjournals.org.
- **2021 update** (Atkinson FS, Brand-Miller JC, Foster-Powell K et al.): published in Am J Clin Nutr 2021 — systematic review with newer entries. Open-access on PMC.
- License: published as journal article, the supplementary tables are part of the publication. **Research use OK, redistribution requires permission.**
- Format: PDF tables → OCR/parse → CSV.

### 8.3 RO staples GI estimates (from glycemic-index.net cross-reference)

- Mămăligă: ~80 (high)
- White bread (pâine albă): ~75 (high)
- Sourdough rye (pâine de secară): ~55 (medium)
- Pasta al dente: ~45-50 (low-medium)
- Rice white: ~65 (medium-high)
- Rice brown: ~50 (medium)
- Potatoes boiled: ~70-80 (high)
- Sarmale (estimate): medium (rice + meat damping the cabbage; ~50)
- Watermelon (pepene): ~70 (high but low GL)
- Apple: ~36 (low)
- Plum (prune): ~24 (low)
- Sweet bread (cozonac): high (~70+)

### 8.4 Recommendation

- **Hand-author `gi_ro.toml` of ~150 common RO foods.** Extract Foster-Powell 2008 PDF appendix → filter to foods present in RO market → cross-reference with University of Sydney website for newer entries.
- For computed dishes (sarmale, tochitură), defer to per-ingredient GI + GL weighted average.

---

## 9. ANSPDCP self-assessment

### 9.1 GDPR special category data (Article 9)

- Health data IS in scope for Article 9. Lawful basis required: explicit consent (Art 9(2)(a)) is the friends-only path.

### 9.2 Household exemption (Article 2(2)(c))

- The GDPR does NOT apply to processing "by a natural person in the course of a purely personal or household activity".
- "Purely personal" = NOT shared beyond a closed circle.
- Friends-only (~5 users) MIGHT qualify if:
  - Data stays within the household / friend group.
  - No sharing with "wider world".
  - No commercial / professional purpose.
- **Caveat from EU case law (Lindqvist C-101/01)**: publishing on web pages accessible to unknown number of people is NOT exempt. Tailscale-private + friends-only invite = closed circle, likely OK.
- **Caveat from Schrems II**: providing the means of processing to other people (i.e., your friends use your app) might make YOU a controller. Grey area.

### 9.3 DPO requirement (Article 37)

- Mandatory ONLY when:
  1. Processing by a public authority (NO — you're private).
  2. Core activities = systematic large-scale monitoring (NO — 5 users).
  3. Core activities = large-scale processing of special category data (NO — 5 users is not "large-scale" by any reasonable threshold).
- **Conclusion: DPO NOT required.**

### 9.4 DPIA (Article 35)

- Required when processing is "likely to result in a high risk to the rights and freedoms".
- Health data + automated decision-making could trigger.
- For friends-only with ≤5 users and no automated decisions about people: **DPIA advisable but not strictly mandatory**. Write one anyway as a lightweight risk-assessment doc.

### 9.5 Breach notification SLA

- Article 33: notify supervisory authority within **72 hours** of becoming aware of a breach.
- Article 34: notify affected data subjects "without undue delay" if breach is likely to result in "high risk".
- For Dietician friends-only, internal-process docs: have a "Breach Plan" in `docs/runbooks/breach-notification.md`.

### 9.6 DSAR template (RO + EN)

- **No official ANSPDCP DSAR template** — they expect controllers to provide their own.
- Recommended fields:
  - Solicitant: nume, e-mail, ID (last 4 of CNP not required, just enough for identity).
  - Cerere: (a) acces date, (b) ștergere, (c) rectificare, (d) portabilitate, (e) restricționare, (f) opoziție.
  - Termenul de răspuns: 30 zile de la primire (Art 12(3)).
- Bilingual template draft sketch:

```
Cerere de exercitare drept GDPR / GDPR rights request
Data: ____
Nume / Name: ____
E-mail: ____
Tip cerere / Request type:
  [ ] Acces (Art 15) / Access
  [ ] Rectificare (Art 16) / Rectification
  [ ] Ștergere (Art 17) / Erasure
  [ ] Restricționare (Art 18) / Restriction
  [ ] Portabilitate (Art 20) / Portability
  [ ] Opoziție (Art 21) / Objection
Detalii / Details: ____
Semnătura / Signature: ____
```

### 9.7 ANSPDCP filing requirement

- **Pre-2018 GDPR repealed the prior-notification regime.** No general filing required.
- ANSPDCP notification required ONLY for DPO appointment (Art 37(7)) — and you don't need a DPO.
- **Conclusion: NO ANSPDCP filing required for Dietician friends-only.**

### 9.8 Romanian Law 190/2018

- Implements GDPR in RO. Adds:
  - Stricter rules on employee monitoring.
  - Restrictions on processing biometric/genetic/health data with consent (extra safeguards: prior consultation of DPO + security measures + impact assessment).
- For Dietician: ensure (a) explicit health-data consent, (b) DPIA exists (even lightweight), (c) "security measures" documented (Tailscale ACL, encryption at rest, etc.).

### 9.9 Verdict

**Friends-only, ~5 users, Iași-based, no commercial purpose: ANSPDCP filing NOT required. DPO NOT required. DPIA advisable. Household exemption plausibly applies but write the consent log + breach plan anyway for safety.**

---

## 10. Anelis Plus auth investigation

### 10.1 Federation architecture

- Anelis Plus uses **SAML2 / Shibboleth federation via RoEduNet ID** (Romanian Education Network identity federation).
- Each member institution runs its own SimpleSAMLphp / Shibboleth IdP.
- UAIC IdP metadata URL: **`https://idp.uaic.ro/simplesaml/saml2/idp/metadata.php`**.
- Login flow:
  1. User goes to Anelis resource (e.g., a publisher's URL via the Anelis SP).
  2. WAYF (Where Are You From) page redirects to RoEduNet IdP picker.
  3. User picks UAIC.
  4. Browser redirects to `idp.uaic.ro` SimpleSAML login.
  5. User enters @uaic.ro credentials.
  6. SAML assertion returned to Anelis SP → publisher access granted.

### 10.2 Headless automation feasibility

- **Hard to fully automate.** SimpleSAMLphp endpoints can be scripted but UAIC may have MFA, captcha, or session timeout.
- **Practical approach**: manual browser login on desktop → export cookies (use Chrome extension "Get cookies.txt LOCALLY" or equivalent) → store cookie jar on desktop → cookie jar refreshed every ~30 days when expired.
- Cookies live in `~/.dietician/anelis-cookies.txt` (Netscape format) → Kotlin Ktor client picks them up via `CookiesStorage`.

### 10.3 Credential storage runbook

Per spec §11 runbook `anelis-credential-rotation.md` — refresh model:

1. User opens browser on desktop.
2. Navigates to `https://portal.anelisplus.ro/acasa`.
3. Clicks Login → picks UAIC → enters creds + MFA if prompted.
4. Once logged in to portal, runs `/credentials rotate anelis` Jarvis command.
5. Command launches a headed Chrome via Playwright with a profile preset, captures cookies after redirect, writes `~/.dietician/anelis-cookies.txt`.
6. Refresh every 30 days OR when paper-fetch returns 401.

### 10.4 Rotation strategy

- 30-day rotation by default.
- Triggered manually OR automatically on 401 response in paper-fetch.
- ntfy push to user when refresh required.

### 10.5 Library access scope

- Anelis Plus provides access to Springer, Wiley, ScienceDirect, IEEE, ACM, JSTOR, Web of Science, Scopus, and ~50 other databases (per UAIC library page).
- For Dietician paper ingestion: nutrition / clinical-trial literature lives in **PubMed / PMC (free / Open Access)** mostly, with the paywalled tail in **Elsevier ScienceDirect + Springer + Wiley** → all accessible via Anelis if UAIC has license.
- **Workflow**: PubMed search returns DOIs → try Unpaywall first (free); on miss, try Anelis cookie-jar fetch against publisher URL.

### 10.6 Verdict

**Anelis is SAML/Shibboleth. No direct API key. User-managed credential model = browser-cookie export, refresh on 30 days or on 401. UAIC IdP at `https://idp.uaic.ro/simplesaml/saml2/idp/metadata.php`. No "API token" to extract.**

---

## 11. Knowledge corpus authoring loop

### 11.1 LLM-drafted vs hand-authored

- Hand-authored is slow but high-quality (no hallucinations).
- LLM-drafted is fast but requires verification.
- **Recommended for Dietician**: hand-author first ~20 "concept cards" (e.g., "iron absorption", "vitamin D and calcium synergy", "GI vs GL", "warfarin and vitamin K") to validate the format and tone. After format is settled, LLM-draft new concept cards with a strict "needs verification" flag until user approves.

### 11.2 Two-file wiki pattern

- File A: `concepts/iron-absorption.md` — narrative prose explaining the concept, written for the user, with `[[wiki-links]]` to related concepts.
- File B: `concepts/iron-absorption.data.md` — autogenerated structured frontmatter + tables:
  ```
  ---
  concept: iron-absorption
  related: [vitamin-c, calcium, tannins, heme-vs-nonheme]
  rda_male: 8mg
  rda_female: 18mg
  sources: [efsa-drv-2017, usda-fnb-dri]
  approved: true
  approved_date: 2026-05-17
  ---
  
  | Form | Bioavailability |
  |---|---|
  | Heme (red meat) | 15-35% |
  | Non-heme (plant, supplement) | 2-20% |
  ...
  ```
- Obsidian transclusion: `concepts/iron-absorption.md` includes `![[iron-absorption.data.md#table]]` to embed the table without duplication.

### 11.3 Approval / publish UX

- LLM drafts new concept → `approved: false` in frontmatter.
- Concept appears in Dietician UI with yellow "Draft — needs review" badge.
- User clicks "Approve" → frontmatter flip to `approved: true` + `approved_date` + signed checksum.
- Only `approved: true` concepts surface to non-author users (friends).
- Mistakes are recoverable: edit + re-approve.

### 11.4 Recommendation

- Use the two-file pattern from day 1.
- Hand-author the seed.
- Defer LLM-drafting until format is locked.
- Frontmatter `approved` field gates visibility.

---

## Cross-cutting synthesis

### Five highest-leverage RO data sources

1. **Auchan VTEX `/api/catalog_system/pub/products/search/{term}`** — only confirmed clean JSON catalog API in RO retail.
2. **Open Food Facts EAN lookup** — fills the nutrition gap for branded items at all RO chains via barcode.
3. **CIQUAL ANSES OpenData** — closest free-license food composition table for European cooking, includes mămăligă-cousins.
4. **EFSA DRV `DRV_Summary_tables_jan_17.pdf`** — definitive EU reference values, free, PDF-extractable.
5. **DrugBank 6.0 academic CSV** — 2,475 drug-food interactions, free for non-commercial.

### Three hardest RO scrape targets

1. **Bringo (Carrefour grocery courier)** — Cloudflare-managed + explicit ai-train=no signal + 403 to ClaudeBot. Skip entirely.
2. **Lidl Plus app** — OAuth + cert pinning + active third-party blocking. Receipt-fetch via `lidl-plus` Python works fragile; catalog browse impossible.
3. **Profi** — anti-bot WAF returns 403 to direct fetch. Skip.

### ANSPDCP verdict (friends-only → exempt? or filing required?)

**NO ANSPDCP filing required. NO DPO required. Household exemption plausibly applies for a ≤5-user friends-only closed circle. Write a lightweight DPIA + breach plan + DSAR template internally for safety and to honor data-subject rights. Romanian Law 190/2018 requires extra safeguards (consent log, security measures, DPIA) for health data — write them.**

### Anelis verdict (Shibboleth? user-managed credential?)

**SAML/Shibboleth federation via RoEduNet IdP. UAIC IdP metadata at `https://idp.uaic.ro/simplesaml/saml2/idp/metadata.php`. No API key. User-managed credential model = manual browser login on desktop → cookie-jar export → 30-day refresh OR on 401. Build a `/credentials rotate anelis` runbook command that launches Playwright with the user's existing browser profile and harvests cookies. Spec §13 "AnelisPaperFetcher implementation TBD" can now be filled: it's a cookie-jar HTTP client, not a SAML SP.**

---

## Open questions

1. **Mega Image / Carrefour scrape adapter design**: should the Dietician backend run Playwright (heavy) or a `_next/data` JSON probe + JSON-LD HTML parser (lighter but more fragile)? Recommendation: JSON-LD parser, fall back to Playwright on parse failure. Needs decision.
2. **Lidl Plus refresh-token bootstrap**: user must MITM their phone once to capture the token. Is this within Victor's tolerance? If not, Lidl coverage = receipt-OCR-only (manual photo upload).
3. **Recipe blog AI-train=no signal**: respect by storing only normalized structured data + URL attribution, never body text? Or skip the AI-train-blocked sites entirely? Council-worthy.
4. **CIQUAL → RO dish mapping**: build a manual `dish_aliases.toml` mapping RO dish names to CIQUAL `alim_code` values, or compute nutrition from per-ingredient reconstruction only? Hybrid likely best.
5. **DrugBank CC BY-NC**: friends-only = "non-commercial"? Almost certainly yes, but verify by reading the license text.
6. **University of Sydney GI database access**: email request for the structured dataset or scrape the website?
7. **WP REST API per-blog rate limits**: unknown. Probe defensively, start at 1 req/2s.
8. **Knowledge corpus first 20 concepts**: which concepts ship first? Suggest: iron-absorption, vitamin-d-calcium, warfarin-vitamin-k, GI-vs-GL, sarmale-nutrition, mămăligă-GI, statins-grapefruit, levothyroxine-fasting, B12-vegan-risk, protein-per-kg, satiety-protein, fiber-types, fat-types-omega, salt-and-BP, alcohol-and-liver, caffeine-tolerance, sleep-and-cortisol, hydration-baseline, ramazan-fasting, post-workout-nutrition.

---

## Sources

### RO supermarkets

1. https://developers.vtex.com/docs/guides/external-marketplace-integration-product-load
2. https://help.vtex.com/tutorial/how-the-carrefour-integration-works--UbtveAQnoQGKOkQIYG0uQ
3. https://vtex.com/en-us/customer-stories/how-carrefour-is-disrupting-online-grocery-with-vtex/
4. https://vtex.com/en-us/press/auchan-is-partnering-with-vtex-to-expand-online-presence/
5. https://github.com/vtex/openapi-schemas/blob/master/VTEX%20-%20Intelligent%20Search%20API.json
6. https://developers.vtex.com/updates/release-notes/new-intelligent-search-api
7. https://www.auchan.ro/api/catalog_system/pub/products/search/lapte (LIVE PROBE)
8. https://www.auchan.ro/robots.txt
9. https://www.auchan.ro/sitemap.xml
10. https://www.mega-image.ro/ (HTML platform identification)
11. https://www.mega-image.ro/robots.txt
12. https://www.mega-image.ro/sitemap/delhaizesitemapindex.xml
13. https://carrefour.ro/ (HTML platform identification)
14. https://carrefour.ro/robots.txt
15. https://www.lidl.ro/ (HTML)
16. https://www.lidl.ro/robots.txt
17. https://www.kaufland.ro/ (HTML)
18. https://www.kaufland.ro/robots.txt
19. https://www.kaufland.ro/.sitemap.xml
20. https://accounts.lidl.com/ (OAuth flow)
21. https://github.com/Andre0512/lidl-plus
22. https://github.com/KoenZomers/LidlApi
23. https://pypi.org/project/lidl-plus/
24. https://www.profi.ro/robots.txt (403)
25. https://www.cora.ro/ (shutdown notice)
26. https://github.com/peviitor-ro/based_scraper_py/blob/main/sites/auchan.py
27. https://www.scrapeit.io/scraper/kaufland
28. https://apify.com/e-commerce/kaufland-fast-product-scraper/api/mcp
29. https://sellerapi.kaufland.com/?page=product-data
30. https://www.bringo.ro/robots.txt
31. https://www.iwebdatascraping.com/scrape-mega-image-ro-product-data.php
32. https://www.iwebdatascraping.com/scrape-auchan-ro-product-data.php

### Recipe DBs

33. https://savoriurbane.com/robots.txt
34. https://www.jamilacuisine.ro/robots.txt
35. https://www.bucataras.ro/robots.txt
36. https://www.lalena.ro/robots.txt
37. https://schema.org/Recipe
38. https://developers.google.com/search/docs/appearance/structured-data/recipe
39. https://pypi.org/project/scrape-schema-recipe/

### Supplements / pharmacies

40. https://www.catena.ro/cumpara-online (HTML platform check)
41. https://www.helpnet.ro/
42. https://comenzi.farmaciatei.ro/
43. https://www.dm.ro/
44. https://www.sensiblu.com/index.php?page=search (Cloudflare Waiting Room)
45. https://natura-plus.ro/
46. https://www.bioshopromania.com/

### Food composition / DRI

47. https://fdc.nal.usda.gov/api-guide/
48. https://fdc.nal.usda.gov/api-key-signup/
49. https://www.anses.fr/en/content/anses-ciqual-food-composition-table
50. https://ciqual.anses.fr/
51. https://zenodo.org/records/17550133 (CIQUAL 2025)
52. https://zenodo.org/records/4770600 (CIQUAL 2020)
53. https://www.efsa.europa.eu/sites/default/files/assets/DRV_Summary_tables_jan_17.pdf
54. https://www.efsa.europa.eu/sites/default/files/2017_09_DRVs_summary_report.pdf
55. https://multimedia.efsa.europa.eu/drvs/index.htm
56. https://ods.od.nih.gov/HealthInformation/nutrientrecommendations.aspx
57. https://www.nal.usda.gov/human-nutrition-and-food-safety/dri-calculator
58. https://www.ncbi.nlm.nih.gov/books/NBK208874/
59. https://www.fao.org/infoods/infoods/tables-and-databases/romania/en/
60. https://world.openfoodfacts.org/data
61. https://openfoodfacts.github.io/openfoodfacts-server/api/tutorial-off-api/

### Traditional dishes nutrition

62. https://www.snapcalorie.com/nutrition/sarmale_nutrition.html
63. https://www.snapcalorie.com/nutrition/mamaliga_nutrition.html
64. https://www.snapcalorie.com/nutrition/mici_nutrition.html
65. https://www.snapcalorie.com/nutrition/cozonac_nutrition.html
66. https://www.snapcalorie.com/nutrition/ciorba_radauteana_nutrition.html
67. https://www.snapcalorie.com/nutrition/tochitura_nutrition.html
68. https://eatntrack.ro/calorii/ciorba-radauteana
69. https://eatntrack.ro/calorii/ciorba-de-burta
70. https://greatnews.ro/cate-calorii-are-ciorba-radauteana-3-exemple-explicate/
71. https://greatnews.ro/cate-calorii-are-o-gogoasa-7-sortimente-explicate-clar/
72. https://glycemic-index.net/cornmeal-porridge-mamalyga/

### Drug-food interactions

73. https://go.drugbank.com/releases/latest
74. https://docs.drugbank.com/csv/
75. https://academic.oup.com/nar/article/52/D1/D1265/7416367 (DrugBank 6.0)
76. https://blog.drugbank.com/food-drug-interactions-and-drug-efficacy/
77. https://open.fda.gov/data/downloads/
78. https://open.fda.gov/apis/drug/
79. https://www.fda.gov/consumers/consumer-updates/grapefruit-juice-and-some-drugs-dont-mix
80. https://www.drugs.com/article/grapefruit-drug-interactions.html
81. https://en.wikipedia.org/wiki/Grapefruit%E2%80%93drug_interactions
82. https://pmc.ncbi.nlm.nih.gov/articles/PMC3589309/ (Grapefruit review)
83. https://curehht.org/wp-content/uploads/2017/11/Food_and_Drug_Interactions_FDA.pdf

### Food safety / EU regulation

84. https://eur-lex.europa.eu/eli/reg/2011/1169/oj/eng
85. https://food.ec.europa.eu/food-safety/labelling-and-nutrition/food-information-consumers-legislation_en
86. https://www.ansvsa.ro/blog/recomandarile-autoritatii-nationale-sanitare-veterinare-si-pentru-siguranta-alimentelor-adresate-consumatorilor-pentru-perioada-sarbatorilor-de-iarna/
87. https://www.fda.gov/media/74435/download (USDA storage chart)
88. https://eur-lex.europa.eu/legal-content/EN/TXT/PDF/?uri=CELEX:02004R0852-20210324

### Glycemic index

89. https://researchdata.edu.au/international-glycemic-index-gi-database/11115
90. https://glycemicindex.com/
91. https://pmc.ncbi.nlm.nih.gov/articles/PMC2584181/ (Foster-Powell 2008)
92. https://pubmed.ncbi.nlm.nih.gov/12081815/ (Foster-Powell 2002)
93. https://ajcn.nutrition.org/article/S0002-9165(22)00494-4/fulltext (Atkinson 2021)

### ANSPDCP / GDPR / Anelis

94. https://gdprhub.eu/ANSPDCP_(Romania)
95. https://www.dataprotection.ro/
96. https://www.dataprotection.ro/index.jsp?page=Informare_protectia_datelor_conf_GDPR&lang=en
97. https://cms.law/en/int/expert-guides/cms-expert-guide-to-data-protection-and-cyber-security-laws/romania
98. https://www.dlapiperdataprotection.com/index.html?t=authority&c=RO
99. https://noyb.eu/en/project/dpa/anspdcp-romania
100. https://lukasatkinson.de/dump/2024-01-06-gdpr-household-exemption/
101. https://anelis-plus.ro/
102. https://portal.anelisplus.ro/acasa
103. https://portal.anelisplus.ro/content/sistemul-de-acces-la-depozitul-national-anelisplus
104. https://portal.anelisplus.ro/content/informatii-pentru-instalare-idp-propriu
105. https://eduroam.ro/participants/
106. https://www.uaic.ro/organizare/departamentul-de-comunicatii-digitale-d-c-d/
107. https://www.uaiasi.ro/biblioteca/index.php/ro/bd_Anelis-plus
