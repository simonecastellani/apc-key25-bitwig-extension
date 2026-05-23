package com.apcsequencer;

/**
 * Sealed marker interface for all high-level sequencer gestures produced by
 * {@link InputModifierTracker}.
 *
 * <p>A gesture represents the user's intent (e.g. "toggle step 3 on track 0")
 * independent of the raw MIDI bytes that triggered it.</p>
 *
 * <p>Permitted subtypes are the concrete record classes that live alongside
 * this interface in the same package.</p>
 */
public sealed interface Gesture
        permits StepToggleGesture, PitchAssignGesture, UndoGesture, RedoGesture,
                LaunchClipGesture, ToggleTransportGesture, StopAllGesture,
                PerStepKnobTurnGesture, TrackStepDurationTurnGesture,
                TrackLoopEndPointGesture, PerTrackKnobTurnGesture,
                ToggleTrackMuteGesture, SetVolumeHeldGesture, SetPanHeldGesture,
                SetSendHeldGesture, SetDeviceHeldGesture,
                DeviceMacroTurnGesture, SendLevelTurnGesture,
                ToggleScaleSelectionOverlayGesture, DismissScaleSelectionOverlayGesture,
                ScaleSelectionPadGesture,
                SetSequenceBankOverlayGesture, SequenceBankPadGesture,
                MoveAllTracksSequenceSlotGesture {
}
