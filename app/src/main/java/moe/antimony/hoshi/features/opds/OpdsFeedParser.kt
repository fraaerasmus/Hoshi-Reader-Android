package moe.antimony.hoshi.features.opds

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Atom/OPDS parser. Every href is resolved against the feed URL (honouring `xml:base` when
 * present) because calibre emits root-relative links (`/get/epub/18/calibre`) with no base.
 */
object OpdsFeedParser {
    private const val AtomNs = "http://www.w3.org/2005/Atom"
    private const val XmlNs = "http://www.w3.org/XML/1998/namespace"
    private const val OpenSearchNs = "http://a9.com/-/spec/opensearch/1.1/"
    private const val SearchTermsToken = "{searchTerms}"

    fun parseFeed(feedUrl: String, xml: ByteArray): OpdsFeed {
        val root = parse(xml)
        require(root.localName == "feed" || root.tagName == "feed") { "Not an Atom feed." }
        val feedBase = baseOf(root, feedUrl)
        val feedLinks = childElements(root, "link").map { toLink(it, feedBase) }
        val entries = childElements(root, "entry").map { entry ->
            val base = baseOf(entry, feedBase)
            OpdsEntry(
                id = text(entry, "id").orEmpty(),
                title = text(entry, "title").orEmpty().ifBlank { "(untitled)" },
                authors = childElements(entry, "author").mapNotNull { text(it, "name") },
                summary = (text(entry, "summary") ?: text(entry, "content"))?.let(::collapse),
                links = childElements(entry, "link").map { toLink(it, base) },
            )
        }
        val searchLink = feedLinks.firstOrNull { it.rel == "search" }
        return OpdsFeed(
            url = feedUrl,
            title = text(root, "title").orEmpty(),
            entries = entries,
            nextHref = feedLinks.firstOrNull { it.rel == "next" }?.href,
            searchTemplate = searchLink?.href?.takeIf { it.contains(SearchTermsToken) },
            searchDescriptionHref = searchLink?.href?.takeIf { !it.contains(SearchTermsToken) },
        )
    }

    /** Template from an OpenSearch description document, preferring the Atom URL. */
    fun parseOpenSearchDescription(documentUrl: String, xml: ByteArray): String? {
        val root = parse(xml)
        val urls = childElements(root, "Url")
        val chosen = urls.firstOrNull { it.getAttribute("type").startsWith("application/atom+xml") } ?: urls.firstOrNull()
        return chosen?.getAttribute("template")?.takeIf { it.isNotBlank() }?.let { resolve(documentUrl, it) }
    }

    fun searchUrl(template: String, query: String): String =
        template.replace(SearchTermsToken, URLEncoder.encode(query, "UTF-8").replace("+", "%20"))

    fun resolve(base: String, href: String): String {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return base
        // Braces from search templates are illegal in URIs; protect them across resolution.
        val protected = trimmed.replace(SearchTermsToken, "__searchTerms__")
        val resolved = runCatching { URI(base).resolve(URI(protected)).toString() }.getOrElse {
            manualResolve(base, protected)
        }
        return resolved.replace("__searchTerms__", SearchTermsToken)
    }

    private fun manualResolve(base: String, href: String): String {
        if (href.contains("://")) return href
        val schemeEnd = base.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return href
        val pathStart = base.indexOf('/', schemeEnd).takeIf { it >= 0 } ?: base.length
        val origin = base.substring(0, pathStart)
        return if (href.startsWith("/")) {
            origin + href
        } else {
            base.substringBeforeLast('/', missingDelimiterValue = base) + "/" + href
        }
    }

    private fun parse(xml: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml)).documentElement
    }

    private fun baseOf(element: Element, inherited: String): String {
        val base = element.getAttributeNS(XmlNs, "base").takeIf { it.isNotBlank() }
            ?: element.getAttribute("xml:base").takeIf { it.isNotBlank() }
            ?: return inherited
        return resolve(inherited, base)
    }

    private fun toLink(link: Element, base: String): OpdsLink =
        OpdsLink(
            href = resolve(base, link.getAttribute("href")),
            rel = link.getAttribute("rel").takeIf { it.isNotBlank() },
            type = link.getAttribute("type").takeIf { it.isNotBlank() },
            title = link.getAttribute("title").takeIf { it.isNotBlank() },
        )

    private fun childElements(parent: Element, localName: String): List<Element> {
        val nodes = parent.childNodes
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE && (it.localName ?: it.nodeName.substringAfter(':')) == localName }
            .map { it as Element }
    }

    private fun text(parent: Element, localName: String): String? =
        childElements(parent, localName).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }

    private fun collapse(text: String): String = text.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
}
