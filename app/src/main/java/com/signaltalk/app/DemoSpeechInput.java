package com.signaltalk.app;

/**
 * 테스트용 음성 입력: 실제 마이크 대신 say()로 문장을 넣는다.
 * (Pleos STT 연결 전까지 데모·테스트에 사용)
 */
public class DemoSpeechInput implements SpeechInput {

    private Listener listener;

    @Override
    public void start(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void stop() {
        listener = null;
    }

    /** 동승자가 말한 것처럼 문장 하나를 넣는다 */
    public void say(String text) {
        if (listener != null) listener.onUtterance(text);
    }
}
