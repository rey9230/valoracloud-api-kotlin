package com.valoracloud.api.notifications.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import org.slf4j.LoggerFactory

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.text.NumberFormat
import java.util.Locale
import jakarta.annotation.PostConstruct

@Service
class HandlebarsTemplateRenderer(
    @Value("\${BRAND_NAME:Valora Cloud}") private val brandName: String,
    @Value("\${BRAND_DOMAIN:valoracloud.com}") private val brandDomain: String,
    @Value("\${FRONTEND_URL:http://localhost:5173}") private val frontendUrl: String,
) {
    private val log = LoggerFactory.getLogger(HandlebarsTemplateRenderer::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val handlebars = Handlebars(ClassPathTemplateLoader("/templates/emails", ""))
    private val messagesCache = mutableMapOf<String, Map<String, String>>()

    @PostConstruct
    fun init() {
        // Preload messages for all supported locales
        loadMessages("en")
        loadMessages("es")
        log.info("✅ Handlebars template renderer initialized with ${messagesCache.size} locales")
    }


    /**
     * Render a template with context data and localization.
     *
     * @param templateName the name of the template file without extension (e.g., "payment-confirmed")
     * @param data the data to pass to the template
     * @param language the language code ("en" or "es")
     * @param subject optional email subject to include in template context
     * @param preheader optional preheader text
     * @param headerLabel optional header label
     * @return the rendered HTML string
     */
    fun render(
        templateName: String,
        data: Map<String, Any>,
        language: String = "en",
        subject: String? = null,
        preheader: String? = null,
        headerLabel: String? = null,
    ): String {
        return try {
            val template = handlebars.compile(templateName)
            val messages = loadMessages(language)

            // Build context with messages, data, and global variables
            val context = buildMap {
                putAll(messages) // All localized messages
                putAll(data) // Template-specific data
                put("brandName", brandName)
                put("brandDomain", brandDomain)
                put("frontendUrl", frontendUrl)
                put("language", language)

                // Override with explicit values if provided
                subject?.let { put("subject", it) }
                preheader?.let { put("preheader", it) }
                headerLabel?.let { put("headerLabel", it) }
            }

            val html = template.apply(context)
            log.debug("✅ Rendered template '$templateName' (${language.uppercase()})")
            html
        } catch (e: Exception) {
            log.error("❌ Failed to render template '$templateName': ${e.message}", e)
            throw IllegalStateException("Template rendering failed for '$templateName'", e)
        }
    }

    /**
     * Load localized messages from JSON file.
     */
    private fun loadMessages(language: String): Map<String, String> {
        return messagesCache.getOrPut(language) {
            try {
                val resource = javaClass.getResourceAsStream("/templates/emails/$language/messages.json")
                    ?: throw IllegalStateException("Messages file not found for language: $language")

                val messages: Map<String, String> = objectMapper.readValue(resource)
                log.debug("Loaded ${messages.size} messages for language: $language")
                messages
            } catch (e: Exception) {
                log.error("Failed to load messages for language '$language': ${e.message}", e)
                emptyMap()
            }
        }
    }

    /**
     * Format currency for display.
     */
    fun formatCurrency(amount: Double, currencyCode: String = "EUR", locale: Locale = Locale.US): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        return formatter.format(amount)
    }
}



