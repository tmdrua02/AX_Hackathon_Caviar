package com.haneul.medassist.controller

import com.haneul.medassist.dto.drug.DrugProductSearchRequest
import com.haneul.medassist.dto.drug.DrugProductSearchResponse
import com.haneul.medassist.service.DrugProductSearchService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/drug-products")
class DrugProductController(
    private val searchService: DrugProductSearchService,
) {
    @PostMapping("/search")
    fun search(
        @Valid @RequestBody request: DrugProductSearchRequest,
    ): DrugProductSearchResponse = searchService.search(request)
}
