package com.duplicatecleaner.di

import android.content.Context
import androidx.room.Room
import com.duplicatecleaner.data.local.DuplicateDao
import com.duplicatecleaner.data.local.DuplicateDatabase
import com.duplicatecleaner.data.repository.DeletionRepositoryImpl
import com.duplicatecleaner.data.repository.DuplicateRepositoryImpl
import com.duplicatecleaner.data.repository.PermissionRepositoryImpl
import com.duplicatecleaner.data.repository.ScanRepositoryImpl
import com.duplicatecleaner.data.repository.SettingsRepositoryImpl
import com.duplicatecleaner.domain.repository.DeletionRepository
import com.duplicatecleaner.domain.repository.DuplicateRepository
import com.duplicatecleaner.domain.repository.PermissionRepository
import com.duplicatecleaner.domain.repository.ScanRepository
import com.duplicatecleaner.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun scanRepository(impl: ScanRepositoryImpl): ScanRepository
    @Binds abstract fun duplicateRepository(impl: DuplicateRepositoryImpl): DuplicateRepository
    @Binds abstract fun permissionRepository(impl: PermissionRepositoryImpl): PermissionRepository
    @Binds abstract fun deletionRepository(impl: DeletionRepositoryImpl): DeletionRepository
    @Binds abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): DuplicateDatabase =
        Room.databaseBuilder(context, DuplicateDatabase::class.java, "duplicates.db")
            .fallbackToDestructiveMigration() // cache only: safe to rebuild by rescanning
            .build()

    @Provides fun dao(db: DuplicateDatabase): DuplicateDao = db.duplicateDao()
}
