package com.morningguard.app.demo

/** Set from [com.morningguard.app.MainActivity] so in-app screens can return to the demo hub. */
object DemoSession {
    var returnToPresenterHub: (() -> Unit)? = null
}
