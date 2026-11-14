package com.signaltalk.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * 빠른 답장을 소리로 읽어 주는 부분.
 * 지금은 안드로이드 기본 TextToSpeech를 시도한다. (Pleos 에뮬레이터에는 기본 엔진이 없어 소리가 안 날 수 있음)
 * 화면 표시는 MainActivity가 항상 한다.
 * 나중에 Pleos TextToSpeech SDK로 교체한다.
 */
public class ReplyOutput {

    private static final String TAG = "SignalTalk";
    private final Context context;
    private TextToSpeech tts;
    private boolean ready = false;

    public ReplyOutput(Context context) {
        this.context = context;
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.KOREAN);
                ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            }
            Log.d(TAG, "TTS ready = " + ready);
        });
    }

    /** @return 실제로 소리를 냈으면 true */
    public boolean speak(String text) {
        Log.d(TAG, "답장 읽기: " + text + " (tts ready=" + ready + ")");
        if (ready) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply");
            return true;
        }
        return false;
    }

    public void release() {
        if (tts != null) tts.shutdown();
    }
}
