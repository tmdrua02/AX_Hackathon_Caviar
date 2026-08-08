package com.haneul.medassist.data

internal fun supplementInteractionResponseJson(
    severity: String = "UNKNOWN",
    explanationStatus: String = "UNAVAILABLE",
    failedSteps: String = "[\"SUPPLEMENT_INGREDIENT_MAPPING_MISSING\"]",
): String =
    """
    {
      "processingStatus": "PARTIAL",
      "severity": "$severity",
      "message": "TEST deterministic message",
      "explanation": {
        "status": "$explanationStatus",
        "summary": "TEST summary",
        "rationale": "TEST rationale",
        "consultationAdvice": "의사 또는 약사와 상담하세요.",
        "keyPoints": [],
        "provider": null,
        "model": null
      },
      "medication": {
        "productCode": "TEST_ITEM_SEQ",
        "productName": "TEST_DRUG_PRODUCT",
        "manufacturer": null,
        "source": {
          "name": "TEST_SOURCE",
          "recordId": "TEST_ITEM_SEQ",
          "retrievedAt": "2026-08-08T00:00:00Z",
          "providerReference": "TEST_PROVIDER_REFERENCE"
        }
      },
      "medicationOverview": null,
      "supplement": {
        "statementNo": "TEST_STTEMNT_NO",
        "productName": "TEST_SUPPLEMENT_PRODUCT",
        "manufacturer": "TEST_MANUFACTURER",
        "registerDate": null,
        "intakeMethod": null,
        "intakeHint": null,
        "mainFunction": null,
        "baseStandard": null,
        "productSource": {
          "name": "TEST_SOURCE",
          "recordId": "TEST_STTEMNT_NO",
          "retrievedAt": "2026-08-08T00:00:00Z",
          "providerReference": "TEST_PROVIDER_REFERENCE"
        },
        "retrievedAt": "2026-08-08T00:00:00Z"
      },
      "drugIngredients": [],
      "supplementIngredients": [],
      "evaluatedPairs": [],
      "matchedRules": [],
      "evidence": [],
      "coverage": {
        "medicationResolved": true,
        "medicationIngredientsExpected": 1,
        "medicationIngredientsResolved": 1,
        "medicationIngredientsComplete": true,
        "supplementResolved": true,
        "supplementIngredientMappingAvailable": false,
        "supplementIngredientsExpected": 0,
        "supplementIngredientsVerified": 0,
        "totalPairs": 0,
        "evaluatedPairs": 0,
        "matchedPairs": 0,
        "failedPairs": 0,
        "ruleRepositoryAvailable": false,
        "complete": false,
        "percentage": 50
      },
      "failedSteps": $failedSteps,
      "catalogMetadata": {
        "available": false,
        "verified": false,
        "catalogVersion": null,
        "schemaVersion": null,
        "catalogChecksum": null,
        "loadedAt": "2026-08-08T00:00:00Z",
        "sourceCount": 0,
        "canonicalIngredientCount": 0,
        "productMappingCount": 0,
        "interactionRuleCount": 0,
        "validationErrorCodes": ["TEST_CATALOG_UNAVAILABLE"]
      },
      "disclaimer": "TEST disclaimer",
      "analyzedAt": "2026-08-08T00:00:00Z"
    }
    """.trimIndent()
