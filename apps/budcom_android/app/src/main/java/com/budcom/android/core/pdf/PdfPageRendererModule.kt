package com.budcom.android.core.pdf

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfPageRendererModule {
    @Binds
    abstract fun bindPdfPageRenderer(
        implementation: AndroidPdfPageRenderer,
    ): PdfPageRenderer
}
