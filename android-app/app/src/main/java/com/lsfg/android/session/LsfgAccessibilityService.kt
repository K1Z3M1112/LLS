package com.lsfg.android.session

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service used to detect foreground app changes and pause/resume
 * frame gen when leaving a whitelisted game.
 * Full implementation in production codebase.
 */
class LsfgAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
