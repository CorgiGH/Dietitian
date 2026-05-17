package com.dietician.scrapers.playwright

import com.dietician.shared.Dietician

/**
 * Playwright scraper subprocess.
 *
 * Invoked from :desktopApp daemon via ProcessBuilder. Reads job spec JSON from stdin,
 * emits JSONL events to stdout (one event per line).
 *
 * Per Council 3 BREAK #16:
 *   - Single Chromium reused across N chains, sequential per-chain
 *   - --single-process --disable-dev-shm-usage
 *   - 4GB RSS ceiling, kill youngest on overrun
 *   - Persistent storageState per chain at storage/<chain>.json
 *
 * Chains supported (Council 4 §10):
 *   - mega-vtex      (Mega Image VTEX search API — HTTP, no browser)
 *   - carrefour-vtex (Carrefour VTEX search API — HTTP, no browser)
 *   - auchan         (Playwright)
 *   - kaufland       (Playwright)
 *   - lidl           (Playwright)
 *   - bringo         (Playwright, separate Bringo-specific account)
 */
fun main(args: Array<String>) {
    println("""{"event":"startup","scraper":"playwright","version":"${Dietician.VERSION}"}""")
    // TODO: parse stdin job, dispatch to chain, emit JSONL events
}
