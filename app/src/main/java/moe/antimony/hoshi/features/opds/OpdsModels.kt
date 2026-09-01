package moe.antimony.hoshi.features.opds

import kotlinx.serialization.Serializable

@Serializable
data class OpdsCatalog(
    val id: String,
    val name: String,
    val url: String,
    val username: String = "",
    val password: String = "",
)

data class OpdsLink(
    val href: String,
    val rel: String?,
    val type: String?,
    val title: String? = null,
) {
    val isAtom: Boolean get() = type?.startsWith("application/atom+xml", ignoreCase = true) == true
    val isEpub: Boolean get() = type?.startsWith("application/epub+zip", ignoreCase = true) == true
    val isAcquisition: Boolean get() = rel?.startsWith("http://opds-spec.org/acquisition") == true
}

data class OpdsEntry(
    val id: String,
    val title: String,
    val authors: List<String>,
    val summary: String?,
    val links: List<OpdsLink>,
) {
    val navigationHref: String? get() = links.firstOrNull { it.isAtom && !it.isAcquisition }?.href
    val epubHref: String? get() = links.firstOrNull { it.isAcquisition && it.isEpub }?.href
    val hasOtherFormatsOnly: Boolean get() = epubHref == null && links.any { it.isAcquisition }
}

data class OpdsFeed(
    val url: String,
    val title: String,
    val entries: List<OpdsEntry>,
    val nextHref: String?,
    /** OpenSearch URL template containing `{searchTerms}`, resolved. */
    val searchTemplate: String?,
    /** OpenSearch description document URL, when the feed only advertises that. */
    val searchDescriptionHref: String?,
)

class OpdsException(message: String, val statusCode: Int? = null) : Exception(message)
