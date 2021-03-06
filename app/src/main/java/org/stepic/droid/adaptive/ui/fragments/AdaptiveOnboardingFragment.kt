package org.stepic.droid.adaptive.ui.fragments

import android.os.Bundle
import android.support.annotation.StringRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_recommendations.*
import org.stepic.droid.R
import org.stepic.droid.adaptive.model.Card
import org.stepic.droid.adaptive.ui.adapters.OnboardingQuizCardsAdapter
import org.stepic.droid.base.FragmentBase
import org.stepic.droid.model.Attempt
import org.stepic.droid.model.Block
import org.stepic.droid.model.Lesson
import org.stepic.droid.model.Step
import org.stepic.droid.ui.util.initCenteredToolbar

class AdaptiveOnboardingFragment: FragmentBase() {
    private val adapter = OnboardingQuizCardsAdapter {
        if (it == 0) onOnboardingCompleted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initOnboardingCards()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater?.inflate(R.layout.fragment_recommendations, container, false)

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cardsContainer.setAdapter(adapter)
        initCenteredToolbar(R.string.adaptive_onboarding_title, showHomeButton = true, homeIndicatorRes = getCloseIconDrawableRes())
    }

    private fun initOnboardingCards() {
        adapter.add(createMockCard(-1, R.string.adaptive_onboarding_card_title_1, R.string.adaptive_onboarding_card_question_1))
        adapter.add(createMockCard(-2, R.string.adaptive_onboarding_card_title_2, R.string.adaptive_onboarding_card_question_2))
        adapter.add(createMockCard(-3, R.string.adaptive_onboarding_card_title_3, R.string.adaptive_onboarding_card_question_3))
        adapter.add(createMockCard(-4, R.string.adaptive_onboarding_card_title_4, R.string.adaptive_onboarding_card_question_4))
    }

    private fun createMockCard(id: Long, @StringRes title_id: Int, @StringRes question_id: Int) : Card {
        val lesson = Lesson()
        lesson.title = getString(title_id)

        val step = Step()
        val block = Block()
        block.text = getString(question_id)
        step.block = block

        return Card(id, 0, lesson, step, Attempt())
    }

    private fun onOnboardingCompleted() {
        activity?.onBackPressed()
    }

    override fun onDestroyView() {
        adapter.detach()
        super.onDestroyView()
    }

    override fun onDestroy() {
        adapter.destroy()
        super.onDestroy()
    }
}