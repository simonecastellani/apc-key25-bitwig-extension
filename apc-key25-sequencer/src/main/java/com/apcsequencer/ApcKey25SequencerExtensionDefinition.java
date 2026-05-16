package com.apcsequencer;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

/**
 * Bitwig extension definition for the APC Key 25 Polyrhythmic Sequencer.
 *
 * <p>Bitwig discovers this class via the {@code Bitwig-Extension-Definition-Class}
 * manifest entry written by the Maven Assembly plugin during {@code mvn package}.</p>
 */
public class ApcKey25SequencerExtensionDefinition extends ControllerExtensionDefinition {

    private static final UUID EXTENSION_UUID =
            UUID.fromString("f3a1b2c4-dead-beef-cafe-000000000001");

    @Override
    public String getName() {
        return "APC Key 25 Polyrhythmic Sequencer";
    }

    @Override
    public String getAuthor() {
        return "apcsequencer";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public UUID getId() {
        return EXTENSION_UUID;
    }

    @Override
    public String getHardwareVendor() {
        return "Akai";
    }

    @Override
    public String getHardwareModel() {
        return "APC Key 25 MK1";
    }

    @Override
    public int getRequiredAPIVersion() {
        return 25;
    }

    @Override
    public int getNumMidiInPorts() {
        return 1;
    }

    @Override
    public int getNumMidiOutPorts() {
        return 1;
    }

    @Override
    public void listAutoDetectionMidiPortNames(
            AutoDetectionMidiPortNamesList list, PlatformType platformType) {
        switch (platformType) {
            case LINUX ->
                    list.add(new String[]{"APC Key 25 MIDI 1"}, new String[]{"APC Key 25 MIDI 1"});
            case MAC ->
                    list.add(new String[]{"APC Key 25"}, new String[]{"APC Key 25"});
            case WINDOWS ->
                    list.add(new String[]{"APC Key 25"}, new String[]{"APC Key 25"});
        }
    }

    @Override
    public ApcKey25SequencerExtension createInstance(ControllerHost host) {
        return new ApcKey25SequencerExtension(this, host);
    }

    @Override
    public String getHelpFilePath() {
        return ""; // no help file yet
    }
}
