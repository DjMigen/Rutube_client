package ru.rutubeclient.core.presentation

import ru.rutubeclient.core.model.Video

/** A video with a resume progress fraction, for the "Continue watching" row. */
data class ContinueItem(val video: Video, val progress: Float)
