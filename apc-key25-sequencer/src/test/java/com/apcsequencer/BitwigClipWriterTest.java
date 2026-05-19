package com.apcsequencer;

import com.bitwig.extension.callback.BooleanValueChangedCallback;
import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.PinnableCursorClip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.*;
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
    private BooleanValueChangedCallback[]     existsCallbacks;
    private SequencerState                    state;
    private ControllerHost                    host;
    private BitwigClipWriter                  writer;

    @BeforeEach
    void setUp() {
        state  = new SequencerState();
        host   = mock(ControllerHost.class);
        clips  = new PinnableCursorClip[TRACK_COUNT];
        existsCallbacks = new BooleanValueChangedCallback[TRACK_COUNT];

        for (int t = 0; t < TRACK_COUNT; t++) {
            PinnableCursorClip clip = mock(PinnableCursorClip.class);
            BooleanValue existsValue = mock(BooleanValue.class);
            when(clip.exists()).thenReturn(existsValue);

            // Capture the callback so tests can fire exists=true/false manually.
            final int idx = t;
            doAnswer(inv -> {
                existsCallbacks[idx] = inv.getArgument(0);
                return null;
            }).when(existsValue).addValueObserver(any(BooleanValueChangedCallback.class));

            clips[t] = clip;
        }

        writer = new BitwigClipWriter(clips, state, host);

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
            for (PinnableCursorClip c : clips) reset(c);

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
            for (PinnableCursorClip c : clips) reset(c);

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
            when(clip.exists()).thenReturn(existsValue);
            final int idx = t;
            doAnswer(inv -> {
                freshCallbacks[idx] = inv.getArgument(0);
                return null;
            }).when(existsValue).addValueObserver(any(BooleanValueChangedCallback.class));
            freshClips[t] = clip;
        }

        BitwigClipWriter freshWriter = new BitwigClipWriter(freshClips, state, host);

        // Write to track 2 before that clip is ready.
        freshWriter.writeStep(2, 5, true, new StepState());

        // Clip should NOT have been called yet.
        verify(freshClips[2], never()).setStep(anyInt(), anyInt(), anyInt(), anyInt(), anyDouble());

        // Now fire exists=true for track 2 — pending write should drain.
        freshCallbacks[2].valueChanged(true);

        verify(freshClips[2], times(1)).setStep(anyInt(), eq(5), anyInt(), anyInt(), anyDouble());
    }
}
