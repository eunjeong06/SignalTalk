package com.signaltalk.app;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * 신호대화(SignalTalk) 운전자 화면
 * - 주행 중: 글자 없이 아이콘만. 질문이 오면 테두리 파란색, 긴급 발화는 화면 전체 빨간 깜박임
 * - 정차 중: 2줄 요약 + 질문 카드(예/아니오/잠깐만) + 차량 기능 버튼
 */
public class MainActivity extends AppCompatActivity
        implements DrivingStateMonitor.Listener, SpeechInput.Listener {

    private static final String PERM_SPEED = "android.car.permission.CAR_SPEED";
    private static final boolean SHOW_DEMO_PANEL = true; // 테스트용 대사 버튼 (시연 후 false)

    // 색
    private static final int BG = Color.parseColor("#101418");
    private static final int CARD = Color.parseColor("#1E252C");
    private static final int TEXT = Color.parseColor("#F2F4F6");
    private static final int SUB = Color.parseColor("#9AA5B1");
    private static final int BLUE = Color.parseColor("#3B82F6");
    private static final int RED = Color.parseColor("#EF4444");
    private static final int GREEN = Color.parseColor("#22C55E");

    private final Handler handler = new Handler(Looper.getMainLooper());

    private DrivingStateMonitor monitor;
    private DemoSpeechInput speechInput;
    private final ConversationAnalyzer analyzer = new ConversationAnalyzer();
    private ReplyOutput reply;

    private boolean stopped = true;

    // 화면 요소
    private FrameLayout frame;          // 테두리를 그리는 영역
    private GradientDrawable border;
    private View drivingView;
    private TextView drivingIcon;
    private ScrollView stoppedView;
    private LinearLayout stoppedContent;
    private TextView urgentOverlay;
    private TextView statusLine;
    private TextView banner;            // 토스트 대신 쓰는 화면 안 알림 띠

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        reply = new ReplyOutput(this);
        speechInput = new DemoSpeechInput();
        speechInput.start(this);

        if (checkSelfPermission(PERM_SPEED) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{PERM_SPEED}, 1);
        } else {
            startMonitor();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        startMonitor();
    }

    private void startMonitor() {
        monitor = new DrivingStateMonitor(this, this);
        monitor.start();
    }

    // ------------------------------------------------------------------
    // 차량 상태
    // ------------------------------------------------------------------
    @Override
    public void onStoppedChanged(boolean nowStopped) {
        boolean wasStopped = stopped;
        stopped = nowStopped;
        if (wasStopped && !nowStopped) {
            // 출발: 답한 대화·일반 대화 원문은 지운다
            analyzer.clearExceptPending();
        }
        render();
    }

    @Override
    public void onValues(float kmh, String gear, boolean parkingBrake) {
        statusLine.setText(String.format("%s · %.0f km/h · 기어 %s · 주차브레이크 %s",
                stopped ? "정차" : "주행", kmh, gear.replace("GEAR_", ""), parkingBrake ? "ON" : "OFF"));
    }

    // ------------------------------------------------------------------
    // 음성 입력
    // ------------------------------------------------------------------
    @Override
    public void onUtterance(String text) {
        ConversationAnalyzer.Utterance u = analyzer.add(text);
        if (u.level == ConversationAnalyzer.Level.URGENT) {
            flashUrgent();
        }
        render();
    }

    @Override
    public void onError(String message) {
        showBanner("음성 인식 오류: " + message, RED);
    }

    // ------------------------------------------------------------------
    // 화면 그리기
    // ------------------------------------------------------------------
    private void render() {
        List<ConversationAnalyzer.Utterance> questions = analyzer.pendingQuestions();

        // 테두리: 답하지 않은 질문이 있으면 파란색
        border.setStroke(dp(questions.isEmpty() ? 0 : 6), BLUE);

        if (stopped) {
            drivingView.setVisibility(View.GONE);
            stoppedView.setVisibility(View.VISIBLE);
            renderStopped(questions);
        } else {
            stoppedView.setVisibility(View.GONE);
            drivingView.setVisibility(View.VISIBLE);
            // 주행 중에는 글자 없이 아이콘만
            drivingIcon.setText(questions.isEmpty() ? "🎧" : "💬");
        }
    }

    private void renderStopped(List<ConversationAnalyzer.Utterance> questions) {
        stoppedContent.removeAllViews();

        // 1) 2줄 요약
        LinearLayout summaryCard = card();
        summaryCard.addView(label("그동안의 대화", SUB, 16, false));
        List<String> lines = analyzer.summarize();
        if (lines.isEmpty()) {
            summaryCard.addView(label("새로운 대화가 없어요", TEXT, 24, false));
        } else {
            for (String l : lines) summaryCard.addView(label("• " + l, TEXT, 26, false));
        }
        stoppedContent.addView(summaryCard);

        // 2) 질문 카드
        for (ConversationAnalyzer.Utterance q : questions) {
            stoppedContent.addView(questionCard(q));
        }
    }

    private View questionCard(ConversationAnalyzer.Utterance q) {
        LinearLayout c = card();
        GradientDrawable bg = (GradientDrawable) c.getBackground();
        bg.setStroke(dp(3), BLUE);

        c.addView(label("❓ 나에게 온 질문", BLUE, 16, true));
        c.addView(label("“" + q.text + "”", TEXT, 28, true));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(replyButton("예", "응, 좋아", q, GREEN));
        row.addView(replyButton("아니오", "아니, 괜찮아", q, RED));
        row.addView(replyButton("잠깐만", "잠깐만 기다려 줘", q, SUB));

        // 차량 기능 연결 버튼
        if (q.action == ConversationAnalyzer.Action.ADD_REST_STOP) {
            row.addView(actionButton("🅿 휴게소 경유지 추가", () ->
                    showBanner("🅿 다음 휴게소를 경유지로 추가했어요 (NaviHelper 연동 예정)", BLUE)));
        } else if (q.action == ConversationAnalyzer.Action.TEMP_UP) {
            row.addView(actionButton("🌡 온도 올리기", () ->
                    showBanner("🌡 온도를 2℃ 올렸어요 (HVAC 연동 예정)", BLUE)));
        } else if (q.action == ConversationAnalyzer.Action.TEMP_DOWN) {
            row.addView(actionButton("❄ 온도 내리기", () ->
                    showBanner("❄ 온도를 2℃ 내렸어요 (HVAC 연동 예정)", BLUE)));
        }
        c.addView(row);
        return c;
    }

    private Button replyButton(String label, String spoken, ConversationAnalyzer.Utterance q, int color) {
        Button b = baseButton(label, color);
        b.setOnClickListener(v -> {
            q.answered = true;
            boolean played = reply.speak(spoken);
            showBanner((played ? "🔊 " : "💬 ") + "보낸 답장: " + spoken, GREEN);
            render();
        });
        return b;
    }

    private Button actionButton(String label, Runnable r) {
        Button b = baseButton(label, BLUE);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private final Runnable hideBanner = () -> banner.setVisibility(View.GONE);

    /** 화면 위쪽에 4초 동안 알림 띠를 보여준다 (차량 OS에서는 토스트가 안 보여서 대신 사용) */
    private void showBanner(String text, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10));
        banner.setBackground(bg);
        banner.setText(text);
        banner.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideBanner);
        handler.postDelayed(hideBanner, 4000);
    }

    private void flashUrgent() {
        urgentOverlay.setVisibility(View.VISIBLE);
        final int[] count = {0};
        handler.post(new Runnable() {
            @Override
            public void run() {
                count[0]++;
                urgentOverlay.setAlpha(count[0] % 2 == 0 ? 1f : 0.35f);
                if (count[0] < 10) {
                    handler.postDelayed(this, 300);
                } else {
                    urgentOverlay.setVisibility(View.GONE);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // 화면 만들기 (XML 없이 코드로)
    // ------------------------------------------------------------------
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        statusLine = label("차량 연결 중…", SUB, 14, false);
        root.addView(statusLine);

        banner = label("", Color.WHITE, 22, true);
        banner.setPadding(dp(16), dp(10), dp(16), dp(10));
        banner.setVisibility(View.GONE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = dp(8);
        root.addView(banner, blp);

        frame = new FrameLayout(this);
        border = new GradientDrawable();
        border.setColor(BG);
        border.setCornerRadius(dp(16));
        frame.setBackground(border);
        frame.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 주행 중 화면: 아이콘만
        LinearLayout dv = new LinearLayout(this);
        dv.setGravity(Gravity.CENTER);
        drivingIcon = label("🎧", TEXT, 96, false);
        dv.addView(drivingIcon);
        drivingView = dv;
        frame.addView(dv, matchParent());

        // 정차 중 화면: 요약 + 질문 카드
        stoppedView = new ScrollView(this);
        stoppedContent = new LinearLayout(this);
        stoppedContent.setOrientation(LinearLayout.VERTICAL);
        stoppedView.addView(stoppedContent);
        frame.addView(stoppedView, matchParent());

        // 긴급 알림 (화면 전체 빨간색)
        urgentOverlay = label("⚠", Color.WHITE, 140, true);
        urgentOverlay.setGravity(Gravity.CENTER);
        urgentOverlay.setBackgroundColor(RED);
        urgentOverlay.setVisibility(View.GONE);
        frame.addView(urgentOverlay, matchParent());

        if (SHOW_DEMO_PANEL) root.addView(demoPanel());

        setContentView(root);
        render();
    }

    /** 테스트용: 동승자 대사를 버튼으로 넣는다 */
    private View demoPanel() {
        String[] lines = {
                "아이가 점심으로 국수 먹고 싶대",
                "오늘 길 엄청 막히네",
                "여보 다음 휴게소 들를까?",
                "여보 좀 춥지 않아?",
                "아빠 멈춰!",
                "여보 몇 시에 도착해?"
        };
        HorizontalScrollView sv = new HorizontalScrollView(this);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, 0);
        row.addView(label("테스트 대사 ▶ ", SUB, 14, false));
        for (String s : lines) {
            Button b = baseButton(s, Color.parseColor("#374151"));
            b.setTextSize(14);
            b.setOnClickListener(v -> speechInput.say(s));
            row.addView(b);
        }
        sv.addView(row);
        return sv;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(16), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView label(String text, int color, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private Button baseButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        b.setPadding(dp(20), dp(10), dp(20), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (monitor != null) monitor.stop();
        if (speechInput != null) speechInput.stop();
        if (reply != null) reply.release();
        super.onDestroy();
    }
}
