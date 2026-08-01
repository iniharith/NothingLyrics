package com.nothing.lyricwidget.widget

/**
 * Second widget provider (main-screen style player widget).
 *
 * Shares all rendering logic with [NothingLyricWidget] — this subclass exists so the
 * framework hands it its own widget IDs via a separate <receiver> / appwidget-provider
 * entry, letting the shared updater distinguish the two sizes by provider class.
 */
class NothingPlayerWidget : NothingLyricWidget()
