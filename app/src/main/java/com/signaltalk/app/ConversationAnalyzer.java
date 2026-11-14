package com.signaltalk.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 규칙 기반 대화 분석기 (단말 안에서만 동작, 원문은 메모리에만 잠깐 보관).
 * - 알림 단계: 일반 / 운전자에게 온 질문 / 긴급
 * - 차량 기능 연결: 휴게소 → 경유지, 춥다/덥다 → 온도
 * - 2줄 요약: 중요한 문장 2개를 골라 짧게 만든다
 *
 * 나중에 Pleos LLM SDK가 열리면 이 클래스만 교체하면 된다.
 */
public class ConversationAnalyzer {

    public enum Level { NORMAL, QUESTION, URGENT }

    public enum Action { NONE, ADD_REST_STOP, TEMP_UP, TEMP_DOWN }

    public static class Utterance {
        public final String text;
        public final Level level;
        public final Action action;
        public final long time;
        public boolean answered = false;

        Utterance(String text, Level level, Action action, long time) {
            this.text = text;
            this.level = level;
            this.action = action;
            this.time = time;
        }
    }

    // ---- 규칙 (필요하면 여기만 고치면 됨) ----
    private static final String[] URGENT_WORDS = {
            "멈춰", "멈추", "위험", "조심", "브레이크", "스톱", "정지", "앞에 사람", "부딪"
    };
    /** 운전자를 부르는 말 (가족 호칭 등) */
    private static final String[] DRIVER_CALLS = {
            "여보", "자기", "아빠", "엄마", "기사님", "운전", "오빠", "언니", "형", "누나"
    };
    /** 질문형 어미: 물음표, ~까, ~래, ~니, ~나, ~어때, ~지, ~요? 등 */
    private static final Pattern QUESTION_END = Pattern.compile(
            ".*(\\?|까|래|니|나|어때|어떄|할래|갈래|줄래|볼래|지|죠|냐|요\\?)\\s*$");
    private static final String[] REST_STOP_WORDS = {"휴게소", "화장실", "쉬었다", "쉬어 가"};
    private static final String[] COLD_WORDS = {"춥", "추워", "추운", "쌀쌀"};
    private static final String[] HOT_WORDS = {"덥", "더워", "더운", "땀"};
    /** 요약할 때 점수를 더 주는 단어 (장소·시간·요청) */
    private static final String[] KEY_WORDS = {
            "휴게소", "화장실", "밥", "점심", "저녁", "먹", "도착", "몇 시", "시간", "어디",
            "전화", "주유", "충전", "들르", "들를", "가자", "약속"
    };

    private final List<Utterance> buffer = new ArrayList<>();

    /** 문장 하나를 분석해서 버퍼에 쌓고 결과를 돌려준다 */
    public Utterance add(String raw) {
        String text = raw == null ? "" : raw.trim();
        Level level = classify(text);
        Action action = detectAction(text);
        Utterance u = new Utterance(text, level, action, System.currentTimeMillis());
        buffer.add(u);
        return u;
    }

    public static Level classify(String text) {
        if (containsAny(text, URGENT_WORDS) || text.endsWith("!!")) return Level.URGENT;
        boolean isQuestion = QUESTION_END.matcher(text).matches();
        if (isQuestion && containsAny(text, DRIVER_CALLS)) return Level.QUESTION;
        // 호칭 없이 차량 관련 요청 질문도 운전자에게 온 질문으로 본다
        if (isQuestion && (containsAny(text, REST_STOP_WORDS)
                || containsAny(text, COLD_WORDS) || containsAny(text, HOT_WORDS))) {
            return Level.QUESTION;
        }
        return Level.NORMAL;
    }

    public static Action detectAction(String text) {
        if (containsAny(text, REST_STOP_WORDS)) return Action.ADD_REST_STOP;
        if (containsAny(text, COLD_WORDS)) return Action.TEMP_UP;
        if (containsAny(text, HOT_WORDS)) return Action.TEMP_DOWN;
        return Action.NONE;
    }

    /** 아직 답하지 않은 질문들 */
    public List<Utterance> pendingQuestions() {
        List<Utterance> out = new ArrayList<>();
        for (Utterance u : buffer) {
            if (u.level == Level.QUESTION && !u.answered) out.add(u);
        }
        return out;
    }

    public int size() {
        return buffer.size();
    }

    /**
     * 2줄 요약: 질문·요청·키워드가 많은 문장 2개를 골라 시간 순서로 보여준다.
     * (질문은 질문 카드로 따로 보여주므로 요약에서는 질문이 아닌 문장을 우선)
     */
    public List<String> summarize() {
        List<Utterance> candidates = new ArrayList<>();
        for (Utterance u : buffer) {
            if (!u.text.isEmpty()) candidates.add(u);
        }
        if (candidates.isEmpty()) return Collections.emptyList();

        List<Utterance> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(ConversationAnalyzer::score).reversed());
        List<Utterance> top = new ArrayList<>(sorted.subList(0, Math.min(2, sorted.size())));
        top.sort(Comparator.comparingLong(u -> u.time));

        List<String> lines = new ArrayList<>();
        for (Utterance u : top) lines.add(shorten(u.text, 28));
        int rest = candidates.size() - top.size();
        if (rest > 0 && lines.size() == 2) {
            lines.set(1, lines.get(1) + "  (외 " + rest + "개)");
        }
        return lines;
    }

    private static int score(Utterance u) {
        int s = 0;
        if (u.level == Level.URGENT) s += 5;
        if (u.action != Action.NONE) s += 3;
        if (u.level == Level.QUESTION) s -= 4; // 질문은 질문 카드로 따로 보여주므로 요약에서는 뒤로
        for (String k : KEY_WORDS) if (u.text.contains(k)) s += 2;
        if (u.text.length() < 4) s -= 3;       // "응", "그래" 같은 짧은 말은 제외
        return s;
    }

    private static String shorten(String t, int max) {
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /** 출발하면 원문을 지운다 (프라이버시: 원문 미저장). 아직 답하지 않은 질문만 남긴다 */
    public void clearExceptPending() {
        buffer.removeIf(u -> !(u.level == Level.QUESTION && !u.answered));
    }

    private static boolean containsAny(String text, String[] words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }
}
