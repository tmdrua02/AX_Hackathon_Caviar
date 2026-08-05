package com.haneul.medassist.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.haneul.medassist.ui.theme.MedAssistTheme
import org.junit.Rule
import org.junit.Test

class InteractionStartButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun disabledBeforeNewMedicationIsConfirmed() {
        compose.setContent { MedAssistTheme { InteractionStartButton(enabled = false) {} } }
        compose.onNodeWithText("동시 복용 확인 시작").assertIsNotEnabled()
    }

    @Test
    fun enabledAfterNewMedicationIsConfirmed() {
        compose.setContent { MedAssistTheme { InteractionStartButton(enabled = true) {} } }
        compose.onNodeWithText("동시 복용 확인 시작").assertIsEnabled()
    }
}
