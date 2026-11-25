package br.com.tveseriesonline

import java.net.URI

object M3uParser {
    private val quotedAttribute = Regex("""([\\w-]+)\\s*=\\s*"([^"]*)"""")
    private val plainAttribute = Regex("""([\\w-]+)\\s*=\\s*([^\\s,]+)""")

    fun parse(content: String): List<Channel> {
        val normalized = content.removePrefix("\uFEFF")
        val lines = normalized.lineSequence().map { it.trim() }.toList()
        val channels = LinkedHashMap<String, Channel>()
        var pendingName: String? = null
        var pendingGroup: String? = null
        var pendingLogo: String? = null
        var pendingTvgId: String? = null
        var explicitGroup: String? = null

        for (line in lines) {
            if (line.isBlank()) continue
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val payload = line.substringAfter(':')
                    val comma = payload.indexOf(',')
                    val attributesPart = if (comma >= 0) payload.substring(0, comma) else payload
                    val displayName = if (comma >= 0) payload.substring(comma + 1).trim() else ""
                    val attrs = linkedMapOf<String, String>()
                    quotedAttribute.findAll(attributesPart).forEach {
                        attrs[it.groupValues[1].lowercase()] = it.groupValues[2]
                    }
                    plainAttribute.findAll(attributesPart).forEach {
                        attrs.putIfAbsent(it.groupValues[1].lowercase(), it.groupValues[2])
                    }
                    pendingName = displayName.ifBlank { attrs["tvg-name"].orEmpty() }
                    pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() }
                    pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    pendingTvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    explicitGroup = line.substringAfter(':').trim().takeIf { it.isNotBlank() }
                }
                line.startsWith("#") -> Unit
                pendingName != null -> {
                    val safeUrl = line.takeIf(::isHttpUrl)
                    if (safeUrl != null) {
                        val name = pendingName!!.ifBlank { safeUrl }
                        channels.putIfAbsent(
                            safeUrl,
                            Channel(
                                streamUrl = safeUrl,
                                name = name,
                                groupName = explicitGroup ?: pendingGroup ?: "Sem categoria",
                                logoUrl = pendingLogo,
                                tvgId = pendingTvgId
                            )
                        )
                    }
                    pendingName = null
                    pendingGroup = null
                    pendingLogo = null
                    pendingTvgId = null
                    explicitGroup = null
                }
            }
        }

        require(channels.isNotEmpty()) {
            "A lista não contém canais HTTP/HTTPS válidos. Verifique se a fonte é uma playlist M3U/M3U8 compatível."
        }
        return channels.values.toList()
    }

    private fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
