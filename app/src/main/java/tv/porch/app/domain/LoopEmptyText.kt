package tv.porch.app.domain

/** What the 24/7 screen says when it has nothing to show. */
object LoopEmptyText {
    /** [providerHasLoop]: 24/7 channels exist but are switched off. [hiddenLoop]: 24/7 groups exist only as hidden groups. */
    fun say(providerHasLoop: Boolean, hiddenLoop: Boolean): String =
        if (providerHasLoop) "No 24/7 channels are showing. Switch them on in Settings > Channels."
        else if (hiddenLoop) "Your 24/7 groups are hidden. Unhide them in Settings > Channels."
        else "Your provider has no 24/7 channels."
}
