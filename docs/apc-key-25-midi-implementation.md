# AKAI PROFESSIONAL APC KEY 25 MK

# COMMUNICATIONS PROTOCOL (v1.0)

APC Key 25 mk2 is a USB bus-powered, compact controller for Ableton Live and other software applications.
It features a 5x8 grid of RGB launch clips, velocity-sensitive keybed, 8 knobs, and 20 UI buttons for software
control.
This document describes the format of messages between the APC Key 25 mk2 and the PC/Mac Host.

## Brief Glossary

**Outbound:** The term “outbound” is used to describe messages sent from the PC Host to the device (i.e., from
the viewpoint of the PC Host).
**Inbound:** The term “inbound” is used to describe messages sent from the device to the PC Host (i.e., from
the viewpoint of the PC Host).

## LED Control via MIDI Messages

APC Key 25 mk2’s pad and button LEDs can be controlled by MIDI Note On/Off messages on Port 1 with 3-
byte messages determining 3 factors: Pad/Button value, Behavior (solid, blink, pulse), and Color (pads =
RGB, buttons = single color LED). You can control individual LEDs or send bulk commands for the entire UI
surface.
Example - If I want to solidly light pad 1 red, send the following:
96 00 05
Yellow = MIDI CH determining LED behavior
Green = Pad/Button Value
Blue = RGB Color as velocity
_*These values will be in HEX_

## Pad/Button Values

Byte 2 (green) in the above example message determines which pad/button you are trying to light up. MIDI
message construction changes depending on if it is an RGB LED (matrix pads) or a Single LED (smaller
square buttons and transport controls).
Use the below guide to determine pad/button values and which type of LED message (RGB or Single) you will
need to send.


## Pad Matrix:

**UI Buttons:**


**RGB LED Behavior (Solid, Blink, Pulse)**

RGB LEDs can be lit solid at varying brightness and blink/pulse at different rates. This behavior is determined
by what MIDI Channel MIDI note on message is sent on (MIDI CH 00-0F).
Looking at our above example, 96 00 05, we see that a value of 96 on byte 1 is used which instructs the LED
to stay solidly lit at 100% brightness. If we change byte 1 to 97, our LED will pulse red at 1/16 notes synced
with an external clock rate.

```
MIDI Channel Byte 1 Value Port Function
0 90 0 On 10% brightness
1 91 0 On 25% brightness
2 92 0 On 50% brightness
3 93 0 On 65% brightness
4 94 0 On 75% brightness
5 95 0 On 90% brightness
6 96 0 On 100% brightness
7 97 0 Pulsing 1/
8 98 0 Pulsing 1/
9 99 0 Pulsing 1/
10 9A 0 Pulsing 1/
11 9B 0 Blinking 1/
12 9C 0 Blinking 1/
13 9D 0 Blinking 1/
14 9E 0 Blinking 1/
15 9F 0 Blinking 1/
```

**RGB LED Color**

The below table designates 128 RGB values assigned to a velocity value which is represented by byte 3 in the
RGB LED message. This color palette is predetermined and cannot be changed.
Again, taking the above example, 96 00 05, let’s change pad 1 to solid lit orange:
96 00 09
96 = Solid lit at 100%
00 = Pad 1
09 = Velocity 09 = Orange

**Velocity to RGB Color Chart**

```
Color Velocity Color Velocity Color Velocity
#000000 0 #142B00 19 #004152 38
#1E1E1E 1 #4CFF4C 20 #001019 39
#7F7F7F 2 #00FF00 21 #4C88FF 40
#FFFFFF 3 #005900 22 #0055FF 41
#FF4C4C 4 #001900 23 #001D59 42
#FF0000 5 #4CFF5E 24 #000819 43
#590000 6 #00FF19 25 #4C4CFF 44
#190000 7 #00590D 26 #0000FF 45
#FFBD6C 8 #001902 27 #000059 46
#FF5400 9 #4CFF88 28 #000019 47
#591D00 10 #00FF55 29 #874CFF 48
#271B00 11 #00591D 30 #5400FF 49
#FFFF4C 12 #001F12 31 #190064 50
#FFFF00 13 #4CFFB7 32 #0F0030 51
#595900 14 #00FF99 33 #FF4CFF 52
#191900 15 #005935 34 #FF00FF 53
#88FF4C 16 #001912 35 #590059 54
#54FF00 17 #4CC3FF 36 #190019 55
#1D5900 18 #00A9FF 37 #FF4C87 56
```

## Color Velocity Color Velocity Color Velocity

- Track Button 1 0x40 0 Red Button Name Note # Channel LED Port #
- Track Button 2 0x41 0 Red
- Track Button 3 0x42 0 Red
- Track Button 4 0x43 0 Red
- Track Button 5 0x44 0 Red
- Track Button 6 0x45 0 Red
- Track Button 7 0x46 0 Red
- Track Button 8 0x47 0 Red
- Scene Launch 1 0x52 0 Green
- Scene Launch 2 0x53 0 Green
- Scene Launch 3 0x54 0 Green
- Scene Launch 4 0x55 0 Green
- Scene Launch 5 0x56 0 Green
- Stop All Clips 0x51 0 None
- Play 0x5b 0 Green
- Record 0x5d 0 Red
- #FF0054 57 #88E106 85 #DCFF6B
- #59001D 58 #72FF15 86 #80FFBD
- #220013 59 #00FF00 87 #9A99FF
- #FF1500 60 #3BFF26 88 #8E66FF
- #993500 61 #59FF71 89 #404040
- #795100 62 #38FFCC 90 #757575
- #436400 63 #5B8AFF 91 #E0FFFF
- #033900 64 #3151C6 92 #A00000
- #005735 65 #877FE9 93 #350000
- #00547F 66 #D31DFF 94 #1AD000
- #0000FF 67 #FF005D 95 #074200
- #00454F 68 #FF7F00 96 #B9B000
- #2500CC 69 #B9B000 97 #3F3100
- #7F7F7F 70 #90FF00 98 #B35F00
- #202020 71 #835D07 99 #4B1502
- #FF0000 72 #392b00 100 #404040
- #BDFF2D 73 #144C10 101 #757575
- #AFED06 74 #0D5038 102 #E0FFFF
- #64FF09 75 #15152A 103 #A00000
- #108B00 76 #16205A 104 #350000
- #00FF87 77 #693C1C 105 #1AD000
- #00A9FF 78 #A8000A 106 #074200
- #002AFF 79 #DE513D 107 #B9B000
- #3F00FF 80 #D86A1C 108 #3F3100
- #7A00FF 81 #FFE126 109 #B35F00
- #B21A7D 82 #9EE12F 110 #4B1502
- #402100 83 #67B50F
- #FF4A00 84 #1E1E30


**Single LED Behavior/Color**

UI Buttons on the periphery of the APC Key 25 mk2 are single-color LEDs and have their own unique control
message.
In this example, we want to solidly light the Track 1 LED, which will always be red.
90 40 01
90 = MIDI Ch 00 _*This will always be 90 for single LED commands. RGB blink/pulse values do not apply._
40 = Volume button value
01 = LED On

Use the below table to construct your single LED MIDI message.

```
Byte number Value Description
1 0x90 MIDI CH Note-On
2 <Button Value> 0x40-0x5B
*See Pad/Button Values above
3 <Velocity> Used to determine LED behavior as follows:
LED Off = 0x
LED On = 0x01, 0x03-0x7F
LED Blink = 0x
```

**LED Bulk Message Format**

**RGB Matrix –** Pad values are constant, MIDI Ch and RGB color are variable.

9X 00 XX
9X 01 XX
9X 02 XX
9X 03 XX
9X 04 XX
9X 05 XX
9X 06 XX
9X 07 XX
9X 08 XX
9X 09 XX
9X 0A XX
9X 0B XX
9X 0C XX
9X 0D XX
9X 0E XX
9X 0F XX
9X 10 XX
9X 11 XX
9X 12 XX
9X 13 XX
9X 14 XX
9X 15 XX
9X 16 XX
9X 17 XX
9X 18 XX
9X 19 XX
9X 1A XX
9X 1B XX
9X 1C XX
9X 1D XX
9X 1E XX
9X 1F XX
9X 20 XX
9X 21 XX
9X 22 XX
9X 23 XX
9X 24 XX
9X 25 XX
9X 26 XX
9X 27 XX

**Single LED UI Buttons –** Button values and MIDI Ch are constant, LED behavior is variable.

90 40 XX
90 41 XX
90 42 XX
90 43 XX
90 44 XX
90 45 XX
90 46 XX
90 47 XX
90 52 XX
90 53 XX
90 54 XX
90 55 XX
90 56 XX
90 51 XX
90 5B XX


**RGB LED Color Lighting**

Alternatively, RGB pad colors can be customized using SysEx commands. This message is sent to the device
to set one or several of the unit’s RGB LEDs to a specific color. The color is created by sending three 8-bit
color values for each color component (Red, Blue, and Green). Standard MIDI only supports 7-bit messages,
so each color in the RGB Color Lighting message will be expressed using MSB/LSB.

```
Byte number Value Description
1 0xF0 MIDI System exclusive message start
2 0x47 Manufacturers ID Byte
3 0x7F System Exclusive Device ID
4 0x4E Product model ID
5 0x24 Message type identifier
6 <total MSB> Number of data bytes to follow (most significant)
7 <total LSB> Number of data bytes to follow (least significant)
8 0x00-0x3F Start Pad
9 0x00-0x3F End Pad
10 0x00-0x7F Red Brightness MSB
11 0x00-0x7F Red Brightness LSB
12 0x00-0x7F Green Brightness MSB
13 0x00-0x7F Green Brightness LSB
14 0x00-0x7F Blue Brightness MSB
15 0x00-0x7F Blue Brightness LSB
... ... Repeat bytes 8-15 for each additional pad color change
<bytes total> 0xF7 MIDI System exclusive message terminator
```

**Generic SysEx Messages**

Depending on the software implementation required, APC Key 25 mk2 will respond to/with the following
SysEx request/ return messages.

**MIDI System Exclusive**

The System Exclusive messages exchanged between the Host and the device will be of the following format:

```
Byte number Value Description
1 0xF0 MIDI System exclusive message start
2 0x47 Manufacturers ID Byte
3 0x7F System Exclusive Device ID
4 00x4E Product model ID
5 <Message ID> Message type identifier
6 <DataLengthMSB> Number of data bytes to follow (most significant)
7 <DataLengthLSB> Number of data bytes to follow (least significant)
8 n data bytes Data field – n bytes long
n +8 0xF7 MIDI System exclusive message terminator
```

**Device Enquiry**

APC Key 25 mk2 supports the standard MMC Device Enquiry message. These System Exclusive messages
are part of the MIDI Machine Control Standard and do not follow the general format for Inbound System
Exclusive message.

```
Byte number Value Description
1 0xF0 MIDI System exclusive message start
2 0x7E Non-Realtime Message
3 0x00 Channel to inquire. (Set to 0 for this protocol.)
4 0x06 Inquiry Message
5 0x01 Inquiry Request
6 0xF7 MIDI System exclusive message terminator
```
APC Key 25 mk2 will respond to a Device Inquiry Request message with the following message:

```
Byte number Value Description
1 0xF0 MIDI System exclusive message start
2 0x7E Non-Realtime Message
3 <MIDI Channel> Common MIDI channel setting
4 0x06 Inquiry Message
5 0x02 Inquiry Response
6 0x47 Manufacturers ID Byte
7 00x4E Product model ID
8 0x00 Number of data bytes to follow (most significant)
9 0x19 Number of data bytes to follow (least significant)
10 <Version1> Software version major most significant
```

**Byte number Value Description**

11 <Version2> Software version major least significant

12 <Version3> Software version minor most significant

13 <Version4> Software version minor least significant

14 <DeviceID> System Exclusive Device ID

15 <Serial1> Serial Number first digit

16 <Serial2> Serial Number second digit

17 <Serial3> Serial Number third digit

18 <Serial4> Serial Number fourth digit

19 <Manufacturing1> Manufacturing Data byte 1

20 <Manufacturing2> Manufacturing Data byte 2

21 <Manufacturing3 Manufacturing Data byte 3

22 <Manufacturing4> Manufacturing Data byte 4

23 <Manufacturing5> Manufacturing Data byte 5

24 <Manufacturing6> Manufacturing Data byte 6

25 <Manufacturing7> Manufacturing Data byte 7

26 <Manufacturing8> Manufacturing Data byte 8

27 <Manufacturing9> Manufacturing Data byte 9

28 <Manufacturing10> Manufacturing Data byte 10

29 <Manufacturing11> Manufacturing Data byte 11

30 <Manufacturing12> Manufacturing Data byte 12

31 <Manufacturing13> Manufacturing Data byte 13

32 <Manufacturing14> Manufacturing Data byte 14

33 <Manufacturing15> Manufacturing Data byte 15

34 <Manufacturing16> Manufacturing Data byte 16

35 0xF7 MIDI System exclusive message terminator


**Introduction Message**

This message is sent before any other device-specific message (i.e., other than Device Enquiry). It instructs
the APC Key 25 mk2 to perform the necessary initialization and informs the firmware of the version number of
the application in order that changes in the application can be catered for in the APC Key 25 mk2 firmware.

```
Byte number Value Description
1 0xF0 MIDI System exclusive message start
2 0x47 Manufacturers ID Byte
3 00x7F System Exclusive Device ID
4 0x4E Product model ID
5 0x60 Message type identifier
6 0x00 Number of data bytes to follow (most significant)
7 0x04 Number of data bytes to follow (least significant)
8 0x00 Application/Configuration identifier
9 <Version High> application Software version major
10 <Version Low> application Software version minor
11 <Bugfix Level> Application Software bug-fix level
12 0xF7 MIDI System exclusive message terminator
```

Response from APC Key 25 mk2 Introduction message:

```
Byte number Value Description
1 0xF0 MIDI System exclusive message start
2 0x47 Manufacturers ID Byte
3 0x7F System Exclusive Device ID
4 0x4E Product model ID
5 0x61 Message type identifier
6 0x00 Number of data bytes to follow (most significant)
7 0x04 Number of data bytes to follow (least significant)
8 <Fader #1 Value> Sends the current value of Fader #1.
9 <Fader #2 Value> Sends the current value of Fader #2.
10 <Fader #3 Value> Sends the current value of Fader #3.
11 <Fader #4 Value> Sends the current value of Fader #4.
12 <Fader #5 Value> Sends the current value of Fader #5.
13 <Fader #6 Value> Sends the current value of Fader #6.
14 <Fader #7 Value> Sends the current value of Fader #7.
15 <Fader #8 Value> Sends the current value of Fader #8.
16 <Fader #9 Value> Sends the current value of Fader #9.
17 0xF7 MIDI System exclusive message terminator
```

**Control Mapping**

Use the below data to determine APC Key 25 mk2 MIDI values corresponding to hardware controls and
modes.

**Pads/Buttons**

The following chart shows the MIDI Note # associated with each button. This number will be used for the
Inbound Note-On/Off values when the buttons are pressed, and Outbound Note-On messages to control the
button’s LEDs, if applicable.

```
Button Name Note # Channel LED Port # Notes
Track Button 1 0x40 0 Red 1
Track Button 2 0x41 0 Red 1
Track Button 3 0x42 0 Red 1
Track Button 4 0x43 0 Red 1
Track Button 5 0x44 0 Red 1
Track Button 6 0x45 0 Red 1
Track Button 7 0x46 0 Red 1
Track Button 8 0x47 0 Red 1
Scene Launch 1 0x52 0 Green 1
Scene Launch 2 0x53 0 Green 1
```
```
Scene Launch Buttons 1-
```
```
Track Buttons 1-
```

```
Button Name Note # Channel LED Port # Notes
Scene Launch 3 0x54 0 Green 1
Scene Launch 4 0x55 0 Green 1
Scene Launch 5 0x56 0 Green 1
Stop All Clips 0x51 0 None 1
Play 0x5b 0 Green 1
Record 0x5d 0 Red 1
Shift 0x62 0 None 1
Oct Down - - - - Transposes keybed by an octave. Does not send Note On/Off.
```
```
Oct Up - - - - Transposes keybed by an octave. Does not send Note On/Off
```
```
Clip Launch
Button 0-
```
```
0x00 –
0x27 See notes RGB 1
```
```
Numbered bottom left to upper right.
MIDI Channel is used to determine LED
illumination Status on CH 00-0F.
See section on RGB LED Behavior.
Sustain 0x40 0 None 0 Midi CC# message
Keybed 0x00 – 0x7f 0 None 0 Full range accessible using Oct Down / Oct Up buttons
```
**Knobs**

The following chart lists the CC# for each knob control.

```
Control Name CC# Channel Port Notes
Knob 1 0x30 0 1 Relative
Knob 2 0x31 0 1 Relative
Knob 3 0x32 0 1 Relative
Knob 4 0x33 0 1 Relative
Knob 5 0x34 0 1 Relative
Knob 6 0x35 0 1 Relative
Knob 7 0x36 0 1 Relative
Knob 8 0x37 0 1 Relative
```

