package org.tasks.filters

import org.tasks.CommonParcelize
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Task
import org.tasks.data.sql.Criterion
import org.tasks.data.sql.QueryTemplate

@CommonParcelize
data class MyTasksFilter(
    override val title: String,
    override var filterOverride: String? = null,
) : AstridOrderingFilter {
    override val icon: Int
        get() = 4 // CustomIcons.ALL_INBOX
    override val sql: String
        get() = QueryTemplate()
            .where(
                Criterion.and(
                    TaskDao.TaskCriteria.activeAndVisible(),
                    Task.PARENT.eq(0)
                )
            ).toString()

    override fun areItemsTheSame(other: FilterListItem): Boolean {
        return other is MyTasksFilter
    }
}