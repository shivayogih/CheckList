package com.dataloom.checklist.di

import com.dataloom.checklist.reminder.AlarmReminderScheduler
import com.dataloom.checklist.reminder.DataStoreReminderStore
import com.dataloom.checklist.reminder.ReminderScheduler
import com.dataloom.checklist.reminder.ReminderStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** List reminders (CL-350): stored in the settings DataStore, delivered by AlarmManager. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    abstract fun bindReminderStore(impl: DataStoreReminderStore): ReminderStore

    @Binds
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler
}
