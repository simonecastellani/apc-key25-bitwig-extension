package com.apcsequencer;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

public class ApcKey25SequencerExtensionDefinition extends ControllerExtensionDefinition {

    private static final UUID EXTENSION_ID =
        UUID.fromString("a4c1b2d3-e5f6-7890-ab12-cd34ef567890");

    @Override public String getName()    { return "APC Key 25 Polyrhythmic Sequencer"; }
    @Override public String getAuthor()  { return "APC Sequencer"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public UUID   getId()      { return EXTENSION_ID; }
    @Override public int    getRequiredAPIVersion() { return 19; }

    @Override public String getHardwareVendor() { return "Akai"; }
    @Override public String getHardwareModel()  { return "APC Key 25"; }
    @Override public int    getNumMidiInPorts()  { return 2; }
    @Override public int    getNumMidiOutPorts() { return 1; }

    @Override
    public void listAutoDetectionMidiPortNames(
            AutoDetectionMidiPortNamesList list, PlatformType platformType) {
        // Each add() call specifies one candidate set of port names.
        // inputNames.length == getNumMidiInPorts(), outputNames.length == getNumMidiOutPorts()
        if (platformType == PlatformType.WINDOWS) {
            list.add(
                new String[]{"APC Key 25", "APC Key 25 MIDI 2"},
                new String[]{"APC Key 25"}
            );
            list.add(
                new String[]{"APC Key 25 MIDI", "APC Key 25 MIDI 2"},
                new String[]{"APC Key 25 MIDI"}
            );
        } else if (platformType == PlatformType.MAC) {
            list.add(
                new String[]{"APC Key 25", "APC Key 25 Port 2"},
                new String[]{"APC Key 25"}
            );
        } else { // Linux
            list.add(
                new String[]{"APC Key 25 MIDI 1", "APC Key 25 MIDI 2"},
                new String[]{"APC Key 25 MIDI 1"}
            );
        }
    }

    @Override
    public ControllerExtension createInstance(ControllerHost host) {
        return new ApcKey25SequencerExtension(this, host);
    }
}
