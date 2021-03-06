package org.stepic.droid.core.presenters.contracts

import org.stepic.droid.model.Submission
import org.stepic.droid.ui.adapters.StepikRadioGroupAdapter

interface CardView {
    fun setSubmission(submission: Submission, animate: Boolean)
    fun onSubmissionConnectivityError()
    fun onSubmissionRequestError()
    fun onSubmissionLoading()

    fun setTitle(title: String?)
    fun setQuestion(html: String?)

    fun getRadioGroupAdapter(): StepikRadioGroupAdapter
}