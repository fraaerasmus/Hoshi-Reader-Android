package moe.antimony.hoshi.features.reader.input

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** How a source is operated; an action only fits a source of the same kind. */
enum class ReaderInputKind { Tap, Hold, Drag }

/** Where a gesture comes from. The bottom row and keyboard keep their own switches; these are the rebindable ones. */
enum class ReaderInputSource(val kind: ReaderInputKind) {
    EdgeLeftHold(ReaderInputKind.Hold),
    EdgeRightHold(ReaderInputKind.Hold),
    EdgeLeftDoubleTap(ReaderInputKind.Tap),
    EdgeRightDoubleTap(ReaderInputKind.Tap),
    EdgeLeftDrag(ReaderInputKind.Drag),
    EdgeRightDrag(ReaderInputKind.Drag),
    VolumeKeyHold(ReaderInputKind.Hold),
}

/** What a gesture does. [kind] null means "fits anywhere" (only [None]). */
enum class ReaderInputAction(val kind: ReaderInputKind?) {
    None(null),
    TogglePlayback(ReaderInputKind.Tap),
    SkipForward(ReaderInputKind.Tap),
    SkipBackward(ReaderInputKind.Tap),
    PageForward(ReaderInputKind.Tap),
    PageBackward(ReaderInputKind.Tap),
    ToggleFocusMode(ReaderInputKind.Tap),
    BoostWhileHeld(ReaderInputKind.Hold),
    Scrub(ReaderInputKind.Drag),
    Brightness(ReaderInputKind.Drag),
    Volume(ReaderInputKind.Drag),
}

enum class SasayakiControlsPlacement { Bottom, Left, Right }

data class ReaderInputBindings(val map: Map<ReaderInputSource, ReaderInputAction> = emptyMap()) {
    fun action(source: ReaderInputSource): ReaderInputAction = map[source] ?: Defaults.getValue(source)

    fun with(source: ReaderInputSource, action: ReaderInputAction): ReaderInputBindings {
        require(action.kind == null || action.kind == source.kind) { "$action does not fit $source" }
        return ReaderInputBindings(map + (source to action))
    }

    fun encode(): String = json.encodeToString(serializer, map.entries.associate { it.key.name to it.value.name })

    companion object {
        val Defaults: Map<ReaderInputSource, ReaderInputAction> = mapOf(
            ReaderInputSource.EdgeLeftHold to ReaderInputAction.BoostWhileHeld,
            ReaderInputSource.EdgeRightHold to ReaderInputAction.BoostWhileHeld,
            ReaderInputSource.EdgeLeftDoubleTap to ReaderInputAction.None,
            ReaderInputSource.EdgeRightDoubleTap to ReaderInputAction.None,
            ReaderInputSource.EdgeLeftDrag to ReaderInputAction.Brightness,
            ReaderInputSource.EdgeRightDrag to ReaderInputAction.Volume,
            ReaderInputSource.VolumeKeyHold to ReaderInputAction.BoostWhileHeld,
        )

        fun compatibleActions(source: ReaderInputSource): List<ReaderInputAction> =
            ReaderInputAction.entries.filter { it.kind == null || it.kind == source.kind }

        /** Before bindings existed, one switch turned the edge brightness/volume drags on (default off). */
        fun fromLegacy(edgeSwipeControls: Boolean): ReaderInputBindings =
            if (edgeSwipeControls) {
                ReaderInputBindings()
            } else {
                ReaderInputBindings()
                    .with(ReaderInputSource.EdgeLeftDrag, ReaderInputAction.None)
                    .with(ReaderInputSource.EdgeRightDrag, ReaderInputAction.None)
            }

        /** Unknown names are dropped; a broken blob yields the defaults rather than a broken reader. */
        fun decode(text: String): ReaderInputBindings =
            runCatching { json.decodeFromString(serializer, text) }
                .getOrDefault(emptyMap())
                .mapNotNull { (source, action) ->
                    val s = ReaderInputSource.entries.firstOrNull { it.name == source } ?: return@mapNotNull null
                    val a = ReaderInputAction.entries.firstOrNull { it.name == action } ?: return@mapNotNull null
                    if (a.kind != null && a.kind != s.kind) null else s to a
                }
                .toMap()
                .let(::ReaderInputBindings)

        private val serializer = MapSerializer(String.serializer(), String.serializer())
        private val json = Json { ignoreUnknownKeys = true }
    }
}
