package org.stepic.droid.ui.activities

import android.support.v4.app.Fragment
import org.stepic.droid.base.SingleFragmentActivity
import org.stepic.droid.model.CollectionDescriptionColors
import org.stepic.droid.model.CoursesCarouselInfo
import org.stepic.droid.model.CoursesDescriptionContainer
import org.stepic.droid.storage.operations.Table
import org.stepic.droid.ui.fragments.CourseCollectionFragment
import org.stepic.droid.ui.fragments.MyCoursesFragment
import org.stepic.droid.ui.fragments.PopularCoursesFragment

class CourseListActivity : SingleFragmentActivity() {
    override fun createFragment(): Fragment {
        val info = intent.getParcelableExtra<CoursesCarouselInfo>(COURSE_LIST_INFO_KEY)
        return when {
            info.table == Table.enrolled -> MyCoursesFragment.newInstance()
            info.table == Table.featured -> PopularCoursesFragment.newInstance()
            info.table == null && info.courseIds != null -> {
                val descriptionContainer = intent.getParcelableExtra<CollectionDescriptionColors?>(COURSE_DESCRIPTION_COLORS)?.let {
                    CoursesDescriptionContainer(info.description, it)
                }
                CourseCollectionFragment.newInstance(info.title, info.courseIds, descriptionContainer)
            }
            else -> throw IllegalStateException("course info is broken")
        }
    }

    companion object {
        const val COURSE_LIST_INFO_KEY = "CourseListInfoKey"
        const val COURSE_DESCRIPTION_COLORS = "CourseDescriptionColors"
    }

    override fun applyTransitionPrev() {
        //no-op
    }
}
