# machine-to-be-a-cursed-other

An amateur neuroscience experiment on visual perception using VR/AR.

> **Inspired by [The Machine To Be Another](https://beanotherlab.org/home/work/tmtba/)**
> by [BeAnotherLab](https://beanotherlab.org/). This is an independent, low-budget "cursed"
> variant that ended up taking another direction.

The camera output from the phone is rendered on the screen. By tapping the screen (or using the main button on the VR viewer), the image is left/right mirrored.

![Video showing VR rendering of LR mirrored camera output](LR-flip.gif)

When being worn like this, it becomes much more difficult to perform basic tasks like picking up objects, drawing pictures, and navigating.

## How to install and use

### Requirements

- a phone running Android 5.0 'Lollipop' or higher
- a Google Cardboard-compatible VR headset, modded to expose the phone camera as pictured here:

![Cardboard VR headset modified to expose the camera](modded-cardboard.jpg)

### Install

#### via pre-built APK (recommended)

Go to the [latest entry in Releases](../../releases/latest), grab **MachineToBeACursedOther-release.apk**
and install manually.

#### via local build (alternative)

Install Android Studio and the following components:

- Android SDK Platform 34
- Build-tools 34
- JDK 17

Open this repo in Android Studio and you should be able to build it and install to your phone.

Or, run this command to build the APK:

```shell
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Calibrate your headset

Your Cardboard viewer should have a QR code on it that looks something like this:

<img width="129" height="129" alt="image" src="https://github.com/user-attachments/assets/827424c7-eee6-40f5-a597-9f308405ceeb" />

Press the `Calibrate` button in the app and scan your headset's QR code to calibrate the VR settings.

### Use

Press the `View` button in the app, insert into your headset, and enjoy!

> [!WARNING]
> Take utmost caution around stairs, tripping hazards, etc. Use in a safe, controlled environment only.

## Fun things to do while using the app

- play pattycake with a friend, try to high five, etc.
- play Jenga, Scrabble, or any game where you have to precisely place small wooden pieces
- play pool/billiards
- print out a [mirror star tracing worksheet](https://www.biointeractive.org/sites/default/files/mirror-tracing-activity-generic.pdf) and draw with a pen
- try to navigate somewhere familiar, like the bathroom or the exit - just have a helper keeping you from tripping/stubbing your toe, etc.

<img width="802" height="1069" alt="image" src="https://github.com/user-attachments/assets/1bd24a34-1ae4-4180-ab25-6529b95b5883" />

## Acknowledgements

This project was originally a low budget implementation of
[The Machine To Be Another](https://beanotherlab.org/home/work/tmtba/) by
[BeAnotherLab](https://beanotherlab.org/).

The original version of the code at [4d04220](https://github.com/metavee/machine-to-be-a-cursed-other/commit/4d04220e981be172fb3f9daf4dfb3c6c6783c300) is pretty rough, as I had no experience with Android, OpenGL, or VR at the time. I used lots of code from these resources, among others:

 * http://www.learnopengles.com/android-lesson-one-getting-started/
 * https://developers.google.com/vr/android/samples/treasure-hunt
 * https://github.com/chauthai/glcam

Since then, things have mostly been vibecoded with Claude.
