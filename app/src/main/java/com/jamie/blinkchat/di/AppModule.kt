package com.jamie.blinkchat.di

import android.content.Context
import com.jamie.blinkchat.BlinkChatApplication // If needed for context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Scopes a_ Hilt component (e.g., application-wide)
object AppModule {

    // Example: Provide Application Context if needed elsewhere (often useful)
    @Singleton
    @Provides
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    // Example: Provide Application instance if needed (less common than context)
    @Singleton
    @Provides
    fun provideApplication(application: BlinkChatApplication): BlinkChatApplication {
        return application
    }

}