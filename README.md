
# Video Trimmer
A modern Android video trimming project built using Kotlin and ExoPlayer. This project demonstrates how to trim videos using a customizable timeline view, allowing users to easily select and cut a segment of their video. It also provides flexible UI customization options for a refined user experience.

**Overview**
This project lets you:

* **Trim Videos:** Select a segment of a video using a custom timeline view.
* **Customize UI Colors:** Adjust the appearance of the time text, play/pause button, and timeline frames with ease.


## Key Features

### Video Trimming

* trimVideo()
Trims the video based on the user-selected start and end positions. This function pauses the video, calculates the appropriate trimming interval, and executes the trimming process on a background thread. When trimming completes successfully, the trimmed video is reloaded into the player.


* Customizable UI

`setTimeTextViewBackgroundColor(String color)`

Customize the background color of the time text view.

`setTimeTextColor(String color)`

Change the color of the displayed time text.

`setPlayPauseIconBackgroundColor(String color)`

Update the background color of the play/pause control button.

##### Timeline Frame Colors:

Easily modify the colors of the timeline view frames using:

binding.rangeSeekBarView.setTopFrameColor(FRAME_BLUE_COLOR)
binding.rangeSeekBarView.setBelowFrameColor(FRAME_YELLOW_COLOR)


Reference Video
![reference_video.mp4](video%2Freference_video.mp4)

# Customization Table

The table below highlights key functions and customization methods used in the project:
| Element                        | Method / Usage                                                   |
|--------------------------------|------------------------------------------------------------------|
| Trimming Video                 | `trimVideo()`                                                   |
|                                | Trims the video based on the selected start and end positions.  |
| Time Text Background Color     | `setTimeTextViewBackgroundColor(String color)`                 |
|                                | Sets the background color for the timer text view.             |
| Time Text Color                | `setTimeTextColor(String color)`                                |
|                                | Sets the color of the timer text.                              |
| Play/Pause Icon Background Color | `setPlayPauseIconBackgroundColor(String color)`             |
|                                | Customizes the play/pause button background color.            |
| Timeline Frame Colors          | `binding.rangeSeekBarView.setTopFrameColor(String color)`      |
|                                | `binding.rangeSeekBarView.setBelowFrameColor(String color)`    |
|                                | Customizes the top and bottom frames of the timeline view.    |





