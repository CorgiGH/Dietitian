# Runbook 10 — Sentinel selector missing on scraper

**Symptom:** `/diag` shows scraper `mega-vtex` (or any chain) status `DEGRADED` or `DISABLED`. Daemon logs show "sentinel selector `.product-card` missing".

**Cause:**
- Chain redesigned page layout / DOM structure
- A/B test serving different markup to scraper UA
- Cloudflare challenge intercepting (anti-bot)
- VTEX API endpoint returning different schema

**User action:**
1. SSH VPS or open desktop: read `state/scraper-last-known-good/<chain>.html`
2. Compare to current: `curl <chain-url> > /tmp/current.html; diff state/scraper-last-known-good/<chain>.html /tmp/current.html`
3. Identify new selectors:
   - Browser DevTools on the live page
   - Find element with price + name + GTIN
   - Update selectors in `:scrapers:playwright/src/main/kotlin/com/dietician/scrapers/playwright/chains/<Chain>Selectors.kt`
4. Update `expectedMinResults` if catalog size changed materially
5. Re-enable: in-app `/scraper enable <chain>`
6. Force a probe: in-app `/scraper probe <chain>` — runs once without committing to baselines until you confirm sample

**Prevention:**
- Sentinel must include product-level anchor (`.product-card` with price + name children), not just chrome (logo + search box)
- Tiered response: 1 broken run → log, 2 → reduce cadence + diff HTML, 3 → disable + alert
- Auto-re-enable probe after 7-day disabled window
- Persist last 3 raw HTML responses per scraper for forensic diff

**If recurring drift:** consider switching to VTEX search API for that chain (Mega, Carrefour — already done). For non-VTEX (Auchan, Kaufland, Lidl), accept periodic maintenance.
