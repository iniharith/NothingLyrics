package com.nothing.lyricwidget.widget

/**
 * Player-widget provider retained separately for existing launcher registrations.
 *
 * Both registered providers use the same player renderer so legacy lyric widgets migrate
 * in place to the media controls and spinning disc after an app update.
 */
class NothingPlayerWidget : NothingLyricWidget()
