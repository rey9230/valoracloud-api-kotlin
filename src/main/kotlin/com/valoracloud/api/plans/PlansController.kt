package com.valoracloud.api.plans

import com.valoracloud.api.common.config.ALL_ADDONS
import com.valoracloud.api.common.config.ALLOWED_TERMS
import com.valoracloud.api.facebook.FacebookConversionsService
import com.valoracloud.api.facebook.ViewContentParams
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/plans")
class PlansController(
    private val plansService: PlansService,
    private val facebookConversions: FacebookConversionsService,
) {
    @GetMapping
    fun findAll(@RequestParam(required = false) type: String?) = plansService.findAll(type)

    @GetMapping("/addons")
    fun getAddons() = mapOf(
        "addons" to ALL_ADDONS,
        "billingTerms" to ALLOWED_TERMS,
    )

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: String, request: HttpServletRequest): Any {
        facebookConversions.trackViewContent(
            ViewContentParams(contentId = id),
            com.valoracloud.api.facebook.FbClientContext(
                clientUserAgent = request.getHeader("User-Agent"),
                clientIpAddress = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                    ?: request.remoteAddr,
                eventSourceUrl = request.getHeader("Referer"),
            ),
        )
        return plansService.findById(id)
    }
}