package com.signaltalk.app;

/**
 * 음성 인식 입력의 "끼우는 자리".
 * 지금은 DemoSpeechInput(버튼으로 대사 넣기)을 쓰고,
 * 나중에 Pleos SpeechToText SDK 구현체로 교체한다.
 */
public interface SpeechInput {

    interface Listener {
        /** 문장 하나가 인식될 때마다 호출 */
        void onUtterance(String text);
        void onError(String message);
    }

    void start(Listener listener);

    void stop();
}
