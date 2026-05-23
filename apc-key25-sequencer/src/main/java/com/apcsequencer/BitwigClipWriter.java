package com.apcsequencer;

import com.bitwig.extension.controller.api.NoteOccurrence;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extension.controller.api.PinnableCursorClip;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Production {@link ClipWriter} backed by an array of Bitwig
 * {@link PinnableCursorClip} objects (one per track).
 *
 * <p>Writes are queued if a clip is not yet ready (exists() = false) and
 * drained the first time exists() fires true for that track.  This covers
 * the window between init() and Bitwig reporting that the auto-created clip
 * is available.</p>
 *
 * <h3>NoteStep API used</h3>
 * <ul>
 *   <li>{@code clip.setStep(channel, x, y, velocity, duration)} — activates a note.</li>
 *   <li>{@code clip.clearStep(channel, x, y)} — removes a note.</li>
 * </ul>
 */
public final class BitwigClipWriter implements ClipWriter {

    private final PinnableCursorClip[] clips;
    private final ClipLauncherSlotBank[] slotBanks;
    private final SettableBooleanValue[] muteValues;
    private final SequencerState       state;

    private final boolean[]            ready;
    private final boolean[]            pendingTrackTiming;
    private final List<Deque<PendingWrite>> pending;
    private final boolean[] trackPlaying;
    private final boolean[] trackMuted;
    private final boolean[][] slotPlaying;
    private PlaybackStateListener playbackStateListener;

    private static final class PendingWrite {
        final int       step;
        final boolean   active;
        final StepState snapshot;

        PendingWrite(int step, boolean active, StepState snapshot) {
            this.step     = step;
            this.active   = active;
            this.snapshot = snapshot;
        }
    }

    /**
     * @param clips one non-null {@link PinnableCursorClip} per track (length == TRACK_COUNT)
     * @param state used to look up per-track {@link StepDuration} for gate-length computation
     */
    public BitwigClipWriter(PinnableCursorClip[] clips, Track[] tracks, SequencerState state) {
        if (clips.length != SequencerState.TRACK_COUNT) {
            throw new IllegalArgumentException(
                    "clips array must have " + SequencerState.TRACK_COUNT + " entries");
        }
        if (tracks.length != SequencerState.TRACK_COUNT) {
            throw new IllegalArgumentException(
                    "tracks array must have " + SequencerState.TRACK_COUNT + " entries");
        }
        this.clips   = clips;
        this.slotBanks = new ClipLauncherSlotBank[SequencerState.TRACK_COUNT];
        this.muteValues = new SettableBooleanValue[SequencerState.TRACK_COUNT];
        this.state   = state;
        this.ready   = new boolean[SequencerState.TRACK_COUNT];
        this.pendingTrackTiming = new boolean[SequencerState.TRACK_COUNT];
        this.pending = new ArrayList<>(SequencerState.TRACK_COUNT);
        this.trackPlaying = new boolean[SequencerState.TRACK_COUNT];
        this.trackMuted = new boolean[SequencerState.TRACK_COUNT];
        this.slotPlaying = new boolean[SequencerState.TRACK_COUNT][TrackState.SLOT_COUNT];

        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            ready[t] = false;
            pending.add(new ArrayDeque<>());
            trackPlaying[t] = false;
            pendingTrackTiming[t] = false;

            final int trackIndex = t;
            Track track = tracks[t];

            ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
            slotBanks[t] = slotBank;
            slotBank.addPlaybackStateObserver((slot, playbackState, queued) -> {
                if (queued || slot < 0 || slot >= TrackState.SLOT_COUNT) {
                    return;
                }
                slotPlaying[trackIndex][slot] = playbackState != 0;
                boolean anyPlaying = false;
                for (int i = 0; i < TrackState.SLOT_COUNT; i++) {
                    if (slotPlaying[trackIndex][i]) {
                        anyPlaying = true;
                        break;
                    }
                }
                if (trackPlaying[trackIndex] != anyPlaying) {
                    trackPlaying[trackIndex] = anyPlaying;
                    if (playbackStateListener != null) {
                        playbackStateListener.onTrackPlayingChanged(trackIndex, anyPlaying);
                    }
                }
            });

            SettableBooleanValue mute = track.mute();
            mute.markInterested();
            mute.addValueObserver(muted -> {
                if (trackMuted[trackIndex] != muted) {
                    trackMuted[trackIndex] = muted;
                    if (playbackStateListener != null) {
                        playbackStateListener.onTrackMutedChanged(trackIndex, muted);
                    }
                }
            });
            muteValues[t] = mute;
            trackMuted[t] = mute.get();

            PinnableCursorClip clip = clips[t];

            clip.exists().markInterested();
            clip.exists().addValueObserver(exists -> {
                if (exists) {
                    setTrackReadyAndDrain(trackIndex, clip);
                } else {
                    ready[trackIndex] = false;
                }
            });

            if (clip.exists().get()) {
                setTrackReadyAndDrain(trackIndex, clip);
            }
        }
    }

    @Override
    public void writeStep(int track, int step, boolean active, StepState stepState) {
        PinnableCursorClip clip = clips[track];

        if (!ready[track] && clip.exists().get()) {
            setTrackReadyAndDrain(track, clip);
        }

        if (!ready[track]) {
            pending.get(track).addLast(new PendingWrite(step, active, stepState.copy()));
            return;
        }

        applyWrite(track, clip, step, active, stepState);
    }

    @Override
    public void writeStepParameters(int track,
                                    int step,
                                    int pitch,
                                    double velocity,
                                    double duration,
                                    double chance,
                                    int repeatCount,
                                    double repeatVelocityEnd,
                                    NoteOccurrence occurrence,
                                    int recurrenceLength,
                                    int recurrenceMask) {
        PinnableCursorClip clip = clips[track];
        NoteStep noteStep = clip.getStep(0, step, pitch);
        noteStep.setVelocity(velocity);
        noteStep.setDuration(duration);
        noteStep.setChance(chance);
        noteStep.setIsChanceEnabled(true);
        noteStep.setRepeatCount(repeatCount);
        noteStep.setRepeatVelocityEnd(repeatVelocityEnd);
        noteStep.setRepeatVelocityCurve(repeatVelocityEnd == 0.0 ? 0.0 : -1.0);
        noteStep.setOccurrence(occurrence);
        noteStep.setIsOccurrenceEnabled(occurrence != NoteOccurrence.ALWAYS);
        if (recurrenceLength > 1) {
            noteStep.setRecurrence(recurrenceLength, recurrenceMask);
            noteStep.setIsRecurrenceEnabled(true);
        } else {
            noteStep.setIsRecurrenceEnabled(false);
        }
        noteStep.setIsRepeatEnabled(repeatCount > 1 || repeatVelocityEnd != 0.0);
    }

    @Override
    public void applyTrackTiming(int track) {
        PinnableCursorClip clip = clips[track];

        if (!ready[track] && clip.exists().get()) {
            setTrackReadyAndDrain(track, clip);
        }

        if (!ready[track]) {
            pendingTrackTiming[track] = true;
            return;
        }

        applyTrackTimingNow(track, clip);
    }

    @Override
    public void toggleTrackClipPlayback(int track, int slot) {
        ClipLauncherSlotBank slotBank = slotBanks[track];
        if (trackPlaying[track]) {
            slotBank.stop();
        } else {
            slotBank.launch(slot);
        }
    }

    @Override
    public void stopAllTrackClips() {
        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            slotBanks[t].stop();
        }
    }

    @Override
    public boolean isTrackPlaying(int track) {
        return trackPlaying[track];
    }

    @Override
    public boolean isTrackMuted(int track) {
        return trackMuted[track];
    }

    @Override
    public void setPlaybackStateListener(PlaybackStateListener listener) {
        playbackStateListener = listener;
        if (listener == null) {
            return;
        }
        for (int t = 0; t < SequencerState.TRACK_COUNT; t++) {
            listener.onTrackPlayingChanged(t, trackPlaying[t]);
            listener.onTrackMutedChanged(t, trackMuted[t]);
        }
    }

    private void applyWrite(int track, PinnableCursorClip clip,
                            int step, boolean active, StepState stepState) {
        TrackState trackState = state.getTrack(track);
        int resolvedStep = resolveStepPosition(step, trackState);
        int effectivePitch = effectivePitch(stepState.getPitch(), trackState.getTranspose());
        double stepBeatTime = trackState.getStepDuration().beatTime();
        double gateDuration = stepState.getGateLength() * stepBeatTime;

        if (active) {
            clip.setStep(0, resolvedStep, effectivePitch, stepState.getVelocity(), gateDuration);
            writeStepParameters(
                    track,
                    resolvedStep,
                    effectivePitch,
                    stepState.getVelocity() / 127.0,
                    gateDuration,
                    clamp(stepState.getProbability() * trackState.getTrackProbability(), 0.0, 1.0),
                    stepState.getRatchetCount(),
                    -stepState.getRatchetDecay(),
                    NoteOccurrence.ALWAYS,
                    recurrenceLength(stepState.getStepCondition()),
                    recurrenceMask(stepState.getStepCondition()));
        } else {
            clip.clearStep(0, resolvedStep, effectivePitch);
        }
    }

    private static int recurrenceLength(StepCondition condition) {
        return switch (condition) {
            case ALWAYS -> 1;
            case EVERY_2ND -> 2;
            case EVERY_4TH -> 4;
            case EVERY_8TH -> 8;
        };
    }

    private static int recurrenceMask(StepCondition condition) {
        return switch (condition) {
            case ALWAYS -> 1;
            case EVERY_2ND -> 0b01;
            case EVERY_4TH -> 0b0001;
            case EVERY_8TH -> 0b00000001;
        };
    }

    private void setTrackReadyAndDrain(int trackIndex, PinnableCursorClip clip) {
        ready[trackIndex] = true;

        Deque<PendingWrite> dq = pending.get(trackIndex);
        while (!dq.isEmpty()) {
            PendingWrite w = dq.poll();
            applyWrite(trackIndex, clip, w.step, w.active, w.snapshot);
        }

        if (pendingTrackTiming[trackIndex]) {
            pendingTrackTiming[trackIndex] = false;
            applyTrackTimingNow(trackIndex, clip);
        }
    }

    private void applyTrackTimingNow(int track, PinnableCursorClip clip) {
        TrackState trackState = state.getTrack(track);
        double stepBeatTime = trackState.getStepDuration().beatTime();
        double baseLoopLength = trackState.getLoopEndPoint() * stepBeatTime;
        double loopLength = baseLoopLength * trackState.getLoopMultiplier().factor();
        double playStart = loopLength * trackState.getPhaseOffset();

        clip.setStepSize(stepBeatTime);
        clip.getLoopLength().set(loopLength);
        clip.getPlayStart().set(playStart);
        clip.getShuffle().set(trackState.getSwing() > 50);

        for (int step = 0; step < TrackState.STEP_COUNT; step++) {
            clip.clearStepsAtX(0, step);
        }

        for (int step = 0; step < TrackState.STEP_COUNT; step++) {
            StepState stepState = trackState.getStep(step);
            if (stepState.isActive()) {
                applyWrite(track, clip, step, true, stepState);
            }
        }
    }

    private static int resolveStepPosition(int step, TrackState trackState) {
        int loopEndPoint = trackState.getLoopEndPoint();
        if (step < 0 || step >= loopEndPoint) {
            return step;
        }
        return Math.floorMod(step + trackState.getPatternRotation(), loopEndPoint);
    }

    private static int effectivePitch(int pitch, int transpose) {
        return (int) clamp(pitch + transpose, 0, 127);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
