package com.backmeupplz.meshtasticplus

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

// Google Play's own rating card. Play decides whether it actually shows and never tells us what the user did.
fun requestReview(activity: Activity) {
    val manager = ReviewManagerFactory.create(activity)
    manager.requestReviewFlow().addOnSuccessListener { manager.launchReviewFlow(activity, it) }
}
