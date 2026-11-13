package com.pocketmaster.ai.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Web search using DuckDuckGo HTML (no API key). Falls back to a generic page fetch
 * if DuckDuckGo is unreachable. Supports Google CSE when configured.
 * Results are cached for 5 minutes.
 */
object WebSearchRepository {

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)

    /** Search results for a query. */
    data class SearchResult(val title: String, val link: String, val snippet: String)

    /** Search a query; returns structured results or empty list. */
    suspend fun search(query: String, provider: String = "DUCK_DUCK_GO"): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            // Check cache
            val cached = cache[query]
            if (cached != null && System.currentTimeMillis() - cached.ts < CACHE_TTL_MS) {
                cached.results
            } else {
                val results = when (provider) {
                    "DUCK_DUCK_GO" -> searchDuckDuckGo(query)
                    "GOOGLE_CSE" -> searchGoogleCse(query)
                    else -> searchDuckDuckGo(query)
                }
                cache[query] = CacheEntry(results, System.currentTimeMillis())
                results
            }
        }
    }

    /** Duckddgo HTML scrape: parses DDG's JSON results wrapper. */
    private suspend fun searchDuckDuckGo(query: String): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://html.duckduckgo.com/html/?q=$encoded")
            val document = try {
                Jsoup.connect(url.toString()).userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36").get()
            } catch (e: Exception) {
                Log.e("WebSearch", "DDG fetch failed: ${e.message}")
                emptyList()
            }
            parseDdgResults(document)
        }
    }

    private fun parseDdgResults(document: Document): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val selector = "div.results_links > div"
        document.select(selector).forEach { linkElem ->
            val a = linkElem.selectFirst("a.result__a")
            val snippet = linkElem.selectFirst("a.result__snippet")
            if (a != null) {
                results.add(SearchResult(
                    title = a.text(),
                    link = a.attr("href"),
                    snippet = snippet?.text().orEmpty()
                ))
            }
        }
        // Fallback: older DDG layout
        if (results.isEmpty()) {
            document.select("li.result").forEach { li ->
                val a = li.selectFirst("a.result__a")
                val snippet = li.selectFirst("a.result__snippet")
                if (a != null) {
                    results.add(SearchResult(a.text(), a.attr("href"), snippet?.text().orEmpty()))
                }
            }
        }
        return results.take(10)
    }

    /** Google Custom Search Engine via CSE JSON (requires CSE ID + API key). */
    private suspend fun searchGoogleCse(query: String): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            val settings = SettingsRepository
            val cseId = settings.googleCseId.value
            val cseKey = settings.googleCseApiKey.value
            if (cseId.isBlank() || cseKey.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://www.googleapis.com/customsearch/v1?key=$cseKey&cx=$cseId&q=$encoded")
            try {
                val jsonText = url.readText()
                parseGoogleResults(jsonText)
            } catch (e: Exception) {
                Log.e("WebSearch", "CSE fetch failed: ${e.message}")
                emptyList()
            }
        }
    }

    private fun parseGoogleResults(jsonText: String): List<SearchResult> {
        return Jsoup.parse(jsonText)
            .select("div.g")
            .map { g ->
                val a = g.selectFirst("a")
                if (a == null) return@map null
                SearchResult(
                    title = g.selectFirst("h3")?.text().orEmpty(),
                    link = a.attr("href"),
                    snippet = a.nextElementSibling()?.text().orEmpty()
                )
            }
            .filterNotNull()
            .take(10)
    }

    private data class CacheEntry(val results: List<SearchResult>, val ts: Long)
}
