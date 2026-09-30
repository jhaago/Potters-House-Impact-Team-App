package org.pottershouse.impactteam.android.ui

import kotlin.test.Test
import kotlin.test.assertFalse

class SeparationFieldTestViewModelTest2 {
    @Test
    fun `field test state defaults inactive`() {
        assertFalse(SeparationFieldTestState().isActive)
    }
}
