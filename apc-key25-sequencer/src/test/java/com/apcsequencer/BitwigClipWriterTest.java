package com.apcsequencer;

import com.bitwig.extension.callback.BooleanValueChangedCallback;
import com.bitwig.extension.callback.ClipLauncherSlotBankPlaybackStateChangedCallback;
import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.NoteOccurrence;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extension.controller.api.PinnableCursorClip;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Regression tests for {@link BitwigClipWriter}.
 *
 * <p>Key invariant: {@code writeStep(track=N, ...)} must call {@code setStep}/{@code clearStep}
 * on {@code clips[N]} and on no other clip.</p>
 */
class BitwigClipWriterTest {

    private static final int TRACK_COUNT = SequencerState.TRACK_COUNT; // 5

    private PinnableCursorClip[]              clips;
    private Track[]                           tracks;
    private BooleanValueChangedCallback[]     existsCallbacks;
    private BooleanValueChangedCallback[]     muteCallbacks;
    private ClipLauncherSlotBankPlaybackStateChangedCallback[] playbackCallbacks;
    private ClipLauncherSlotBank[]            slotBanks;
    private SettableBooleanValue[]            muteValues;
    private SequencerState                    state;
    private BitwigClipWriter                  writer;

    @BeforeEach
    void setUp() {
        state  = new SequencerState();
        clips  = new PinnableCursorClip[TRACK_COUNT];
        tracks = new Track[TRACK_COUNT];
        existsCallbacks = new BooleanValueChangedCallback[TRACK_COUNT];
        muteCallbacks = new BooleanValueChangedCallback[TRACK_COUNT];
        playbackCallbacks = new ClipLauncherSlotBankPlaybackStateChangedCallback[TRACK_COUNT];
        slotBanks = new ClipLauncherSlotBank[TRACK_COUNT];
        muteValues = new SettableBooleanValue[TRACK_COUNT];

        for (int t = 0; t < TRACK_COUNT; t++) {
            PinnableCursorClip clip = mock(PinnableCursorClip.class);
            BooleanValue existsValue = mock(BooleanValue.class);
            NoteStep noteStep = mock(NoteStep.class);
            when(clip.exists()).thenReturn(existsValue);
            when(clip.getStep(anyInt(), anyInt(), anyInt())).thenReturn(noteStep);
            when(existsValue.get()).thenReturn(false);

            // Capture the callback so tests can fire exists=true/false manually.
            final int idx = t;
            doAnswer(inv -> {
                existsCallbacks[idx] = inv.getArgument(0);
                return null;
            }).when(existsValue).addValueObserver(any(BooleanValueChangedCallback.class));

            clips[t] = clip;

            Track track = mock(Track.class);
            ClipLauncherSlotBank slotBank = mock(ClipLauncherSlotBank.class);
            SettableBooleanValue mute = mock(SettableBooleanValue.class);
            when(track.clipLauncherSlotBank()).thenReturn(slotBank);
            when(track.mute()).thenReturn(mute);
            when(mute.get()).thenReturn(false);

            final int idxTrack = t;
            doAnswer(inv -> {
                playbackCallbacks[idxTrack] = inv.getArgument(0);
                return null;
            }).when(slotBank).addPlaybackStateObserver(any(ClipLauncherSlotBankPlaybackStateChangedCallback.class));

            doAnswer(inv -> {
                muteCallbacks[idxTrack] = inv.getArgument(0);
                return null;
            }).when(mute).addValueObserver(any(BooleanValueChangedCallback.class));

            tracks[t] = track;
            slotBanks[t] = slotBank;
            muteValues[t] = mute;
        }

        writer = new BitwigClipWriter(clips, tracks, state);

        // Mark all clips ready (simulate Bitwig reporting exists=true).
        for (int t = 0; t < TRACK_COUNT; t++) {
            existsCallbacks[t].valueChanged(true);
        }
    }

    // -----------------------------------------------------------------------
    // Routing: writeStep(track=N) reaches clips[N] and no other clip
    // -----------------------------------------------------------------------

    @Test
    void writeStep_routes_to_correct_clip_for_each_track() {
        StepState stepState = new StepState();

        for (int targetTrack = 0; targetTrack < TRACK_COUNT; targetTrack++) {
            // Reset call counts.
            for (PinnableCursorClip c : clips) clearInvocations(c);

            writer.writeStep(targetTrack, 0, true, stepState);

            // Only clips[targetTrack] should have received setStep.
            verify(clips[targetTrack], times(1))
                    .setStep(anyInt(), anyInt(), anyInt(), anyInt(), anyDouble());

            for (int other = 0; other < TRACK_COUNT; other++) {
                if (other != targetTrack) {
                    verify(clips[other], never())
                            .setStep(anyInt(), anyInt(), anyInt(), anyInt(), anyDouble());
                }
            }
        }
    }

    @Test
    void writeStep_clear_routes_to_correct_clip() {
        StepState stepState = new StepState();

        for (int targetTrack = 0; targetTrack < TRACK_COUNT; targetTrack++) {
            for (PinnableCursorClip c : clips) clearInvocations(c);

            writer.writeStep(targetTrack, 3, false, stepState);

            verify(clips[targetTrack], times(1)).clearStep(anyInt(), eq(3), anyInt());

            for (int other = 0; other < TRACK_COUNT; other++) {
                if (other != targetTrack) {
                    verify(clips[other], never()).clearStep(anyInt(), anyInt(), anyInt());
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Pending-write queue: writes before exists=true are drained on ready
    // -----------------------------------------------------------------------

    @Test
    void writes_before_exists_are_queued_and_drained_when_clip_becomes_ready() {
        // Create a fresh writer where clips are NOT yet ready.
        PinnableCursorClip[] freshClips = new PinnableCursorClip[TRACK_COUNT];
        BooleanValueChangedCallback[] freshCallbacks = new BooleanValueChangedCallback[TRACK_COUNT];

        for (int t = 0; t < TRACK_COUNT; t++) {
            PinnableCursorClip clip = mock(PinnableCursorClip.class);
            BooleanValue existsValue = mock(BooleanValue.class);
            NoteStep noteStep = mock(NoteStep.class);
            when(clip.exists()).thenReturn(existsValue);
            when(clip.getStep(anyInt(), anyInt(), anyInt())).thenReturn(noteStep);
            final int idx = t;
            doAnswer(inv -> {
                freshCallbacks[idx] = inv.getArgument(0);
                return null;
            }).when(existsValue).addValueObserver(any(BooleanValueChangedCallback.class));
            when(existsValue.get()).thenReturn(false);
            freshClips[t] = clip;
        }

        Track[] freshTracks = new Track[TRACK_COUNT];
        for (int t = 0; t < TRACK_COUNT; t++) {
            Track track = mock(Track.class);
            ClipLauncherSlotBank slotBank = mock(ClipLauncherSlotBank.class);
            SettableBooleanValue mute = mock(SettableBooleanValue.class);
            when(track.clipLauncherSlotBank()).thenReturn(slotBank);
            when(track.mute()).thenReturn(mute);
            when(mute.get()).thenReturn(false);
            doNothing().when(slotBank).addPlaybackStateObserver(any(ClipLauncherSlotBankPlaybackStateChangedCallback.class));
            doNothing().when(mute).addValueObserver(any(BooleanValueChangedCallback.class));
            freshTracks[t] = track;
        }

        BitwigClipWriter freshWriter = new BitwigClipWriter(freshClips, freshTracks, state);

        // Write to track 2 before that clip is ready.
        freshWriter.writeStep(2, 5, true, new StepState());

        // Clip should NOT have been called yet.
        verify(freshClips[2], never()).setStep(anyInt(), anyInt(), anyInt(), anyInt(), anyDouble());

        // Now fire exists=true for track 2 — pending write should drain.
        freshCallbacks[2].valueChanged(true);

        verify(freshClips[2], times(1)).setStep(anyInt(), eq(5), anyInt(), anyInt(), anyDouble());
    }

    @Test
    void writeStep_applies_immediately_when_clip_already_exists_even_without_observer_callback() {
        PinnableCursorClip[] freshClips = new PinnableCursorClip[TRACK_COUNT];

        for (int t = 0; t < TRACK_COUNT; t++) {
            PinnableCursorClip clip = mock(PinnableCursorClip.class);
            BooleanValue existsValue = mock(BooleanValue.class);
            NoteStep noteStep = mock(NoteStep.class);
            when(clip.exists()).thenReturn(existsValue);
            when(clip.getStep(anyInt(), anyInt(), anyInt())).thenReturn(noteStep);

            boolean trackIsReady = t == 1;
            when(existsValue.get()).thenReturn(trackIsReady);
            doNothing().when(existsValue).addValueObserver(any(BooleanValueChangedCallback.class));

            freshClips[t] = clip;
        }

        Track[] freshTracks = new Track[TRACK_COUNT];
        for (int t = 0; t < TRACK_COUNT; t++) {
            Track track = mock(Track.class);
            ClipLauncherSlotBank slotBank = mock(ClipLauncherSlotBank.class);
            SettableBooleanValue mute = mock(SettableBooleanValue.class);
            when(track.clipLauncherSlotBank()).thenReturn(slotBank);
            when(track.mute()).thenReturn(mute);
            when(mute.get()).thenReturn(false);
            doNothing().when(slotBank).addPlaybackStateObserver(any(ClipLauncherSlotBankPlaybackStateChangedCallback.class));
            doNothing().when(mute).addValueObserver(any(BooleanValueChangedCallback.class));
            freshTracks[t] = track;
        }

        BitwigClipWriter freshWriter = new BitwigClipWriter(freshClips, freshTracks, state);

        freshWriter.writeStep(1, 2, true, new StepState());

        verify(freshClips[1], times(1)).setStep(anyInt(), eq(2), anyInt(), anyInt(), anyDouble());
    }

    @Test
    void toggleTrackClipPlayback_launches_slot_when_track_not_playing() {
        writer.toggleTrackClipPlayback(1, 3);

        verify(slotBanks[1]).launch(3);
        verify(slotBanks[1], never()).stop();
    }

    @Test
    void toggleTrackClipPlayback_stops_track_when_any_slot_is_playing() {
        playbackCallbacks[1].playbackStateChanged(4, 1, false);

        writer.toggleTrackClipPlayback(1, 3);

        verify(slotBanks[1]).stop();
        verify(slotBanks[1], never()).launch(anyInt());
    }

    @Test
    void stopAllTrackClips_stops_each_track_slot_bank() {
        writer.stopAllTrackClips();

        for (int t = 0; t < TRACK_COUNT; t++) {
            verify(slotBanks[t]).stop();
        }
    }

    @Test
    void playback_state_observer_updates_track_playing_flag() {
        playbackCallbacks[2].playbackStateChanged(0, 1, false);
        assertTrue(writer.isTrackPlaying(2));

        playbackCallbacks[2].playbackStateChanged(0, 0, false);
        assertFalse(writer.isTrackPlaying(2));
    }

    @Test
    void mute_observer_updates_track_muted_flag() {
        muteCallbacks[3].valueChanged(true);
        assertTrue(writer.isTrackMuted(3));

        muteCallbacks[3].valueChanged(false);
        assertFalse(writer.isTrackMuted(3));
    }

    @Test
    void write_step_parameters_sets_notestep_fields() {
        NoteStep noteStep = mock(NoteStep.class);
        when(clips[0].getStep(anyInt(), anyInt(), anyInt())).thenReturn(noteStep);

        writer.writeStepParameters(0, 2, 60, 0.8, 0.25, 0.5, 4, -0.5,
                NoteOccurrence.FIRST, 4, 0b0001);

        verify(noteStep).setVelocity(0.8);
        verify(noteStep).setDuration(0.25);
        verify(noteStep).setChance(0.5);
        verify(noteStep).setIsChanceEnabled(true);
        verify(noteStep).setRepeatCount(4);
        verify(noteStep).setRepeatVelocityEnd(-0.5);
        verify(noteStep).setOccurrence(NoteOccurrence.FIRST);
        verify(noteStep).setIsOccurrenceEnabled(true);
        verify(noteStep).setRecurrence(4, 0b0001);
        verify(noteStep).setIsRecurrenceEnabled(true);
    }
}
