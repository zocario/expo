package expo.modules.camera

import android.content.Context
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaActionSound
import android.media.SoundPool
import android.os.Build
import java.io.File

/**
 * Plays the shutter click the way the phone's other UI sounds play.
 *
 * `MediaActionSound` plays with `FLAG_AUDIBILITY_ENFORCED`: on many phones it ignores
 * silent and vibrate modes and the volume. Its sound file is played here as a plain
 * sonification instead, which follows the system volume, and nothing is played unless
 * the ringer mode is normal. Where the shutter sound is required by law, `MediaActionSound`
 * is kept. Create and use on the main thread.
 */
internal class ShutterSound(context: Context) {
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val soundPool = SoundPool.Builder()
    .setMaxStreams(1)
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    )
    .build()
  private var soundId = 0
  private var isLoaded = false
  private var playWhenLoaded = false
  private var enforcedSound: MediaActionSound? = null

  init {
    soundPool.setOnLoadCompleteListener { _, id, status ->
      if (id != soundId || status != 0) {
        return@setOnLoadCompleteListener
      }
      isLoaded = true
      if (playWhenLoaded) {
        playWhenLoaded = false
        playClick()
      }
    }
    // The directories MediaActionSound reads its sounds from.
    soundId = listOf("/product/media/audio/ui", "/system/media/audio/ui")
      .map { File(it, "camera_click.ogg") }
      .firstOrNull { it.canRead() }
      ?.let { soundPool.load(it.path, 1) }
      ?: 0
  }

  fun play() {
    if (mustPlayShutterSound()) {
      playEnforced()
      return
    }
    if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
      return
    }
    when {
      soundId == 0 -> playEnforced()
      isLoaded -> playClick()
      else -> playWhenLoaded = true
    }
  }

  fun release() {
    soundPool.release()
    enforcedSound?.release()
    enforcedSound = null
  }

  private fun playClick() {
    soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
  }

  private fun playEnforced() {
    val sound = enforcedSound ?: MediaActionSound().also {
      it.load(MediaActionSound.SHUTTER_CLICK)
      enforcedSound = it
    }
    sound.play(MediaActionSound.SHUTTER_CLICK)
  }

  private fun mustPlayShutterSound(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return MediaActionSound.mustPlayShutterSound()
    }
    // MediaActionSound.mustPlayShutterSound() is Android 13+. Before, read the setting the
    // audio service uses to force the sound (it can also be forced by the
    // audio.camerasound.force system property, which apps cannot read).
    val id = Resources.getSystem().getIdentifier("config_camera_sound_forced", "bool", "android")
    return id != 0 && Resources.getSystem().getBoolean(id)
  }
}
