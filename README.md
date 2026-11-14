# 신호대화 (SignalTalk)

> 청각장애인 운전자가 **차가 멈춘 순간에만** 동승자와의 대화를 2줄 요약과 질문 카드로 확인하고, 버튼 한 번으로 답장할 수 있게 해 주는 차량용 대화 연결 앱

현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품작

---

## 주요 기능

| 기능 | 설명 | 현재 상태 |
|---|---|---|
| 정차 감지 | 차속 0 유지(2초) / 기어 P / 주차 브레이크로 정차 판단. 정차 시에만 요약 표시, 출발 시 즉시 숨김 | ✅ 차량 데이터(VHAL)로 동작 |
| 3단계 알림 | 일반 대화는 누적, 운전자에게 온 질문은 파란 테두리, 긴급 발화("멈춰!")는 주행 중에도 화면 전체 빨간 깜박임 | ✅ |
| 2줄 요약 | 정차 시 그동안의 대화 중 중요한 문장 2개를 골라 표시 (단말 안에서 규칙 기반 처리, 원문은 출발 시 삭제) | ✅ |
| 질문 카드 + 빠른 답장 | [예] [아니오] [잠깐만] 선택 시 답장 전달 | ✅ 화면 표시 / 🔜 음성(TTS) 연동 예정 |
| 차량 기능 연결 | "휴게소" → 경유지 추가 버튼, "춥다/덥다" → 온도 조절 버튼 | ✅ 버튼 / 🔜 NaviHelper·HVAC 연동 예정 |
| 음성 인식 | 동승자 발화를 텍스트로 변환 | 🔜 현재는 **테스트 대사 버튼**으로 대체 (Pleos SpeechToText SDK로 교체 예정) |

> 현재 버전은 음성 인식 대신 화면 아래 **"테스트 대사"** 버튼으로 동승자 발화를 입력합니다.
> 음성 입력은 `SpeechInput` 인터페이스로 분리되어 있어 Pleos SpeechToText SDK 구현체로 교체할 수 있습니다.

---

## APK

- 위치: [`apk/장은정_신호대화_release.apk`](apk/장은정_신호대화_release.apk)

---

## 테스트 환경

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** (Pleos Connect v2.1.0 APIs, Android API 34, x86_64) |
| 화면 규격 | **2560 × 1440** (Primary display) |
| 개발 도구 | Android Studio, Java 11 |
| minSdk / targetSdk | 29 / 37 |
| AVD 설정 | Boot option: **Cold boot** (Quick boot에서는 VHAL 사용 불가), Graphics: Hardware, RAM 4GB 이상 |

### 사용 권한
- `android.car.permission.CAR_SPEED` (차량 속도)
- `android.car.permission.CAR_POWERTRAIN` (기어, 주차 브레이크)

---

## 실행 방법

### 1. APK 설치
에뮬레이터를 켠 상태에서 (Windows cmd, `platform-tools` 폴더):

```
adb install -r 장은정_신호대화_release.apk
```

### 2. 권한 허용
앱 첫 실행 시 차량 속도 권한 팝업에서 **허용**을 누릅니다.
팝업이 보이지 않으면 아래 명령으로 권한을 줄 수 있습니다. (Pleos 에뮬레이터 사용자 번호: 10)

```
adb shell pm grant --user 10 com.signaltalk.app android.car.permission.CAR_SPEED
```

### 3. 앱 실행
런처에서 **SignalTalk**를 누르거나:

```
adb shell am start --user 10 -n com.signaltalk.app/.MainActivity
```

### 4. 주행 상태 바꾸기 (Pleos Connect Virtual Console)

```
adb forward tcp:8088 tcp:8088
```

PC 브라우저에서 `http://127.0.0.1:8088/` 접속 → 오른쪽 위 **P R N D** 와 **ACCEL / BRAKE** 로 주행·정차를 바꿉니다.

### 5. 시연 순서
1. 정차(P): "그동안의 대화" 카드 표시
2. **D + ACCEL** → 주행: 글자 없이 🎧 아이콘만 표시
3. 테스트 대사 **"여보 다음 휴게소 들를까?"** → 💬 아이콘 + 파란 테두리
4. 테스트 대사 **"아빠 멈춰!"** → 화면 전체 빨간 깜박임
5. **BRAKE** 로 정지 → 2초 뒤 2줄 요약 + 질문 카드
6. 질문 카드 **[예]** → "보낸 답장" 표시, **[🅿 휴게소 경유지 추가]** → 결과 표시

---

## 코드 구조

```
app/src/main/java/com/signaltalk/app/
├── MainActivity.java          # 운전자 화면 (주행: 아이콘만 / 정차: 요약·질문 카드)
├── DrivingStateMonitor.java   # 속도·기어·주차 브레이크 구독 → 정차/주행 판단
├── ConversationAnalyzer.java  # 알림 단계 분류, 차량 기능 감지, 2줄 요약 (규칙 기반)
├── SpeechInput.java           # 음성 입력 인터페이스 (Pleos STT로 교체할 자리)
├── DemoSpeechInput.java       # 테스트 대사 버튼 입력
└── ReplyOutput.java           # 답장 음성 출력 (Pleos TTS로 교체 예정)
```

## 향후 계획
- Pleos SpeechToText SDK 연동 (실시간 받아쓰기)
- Pleos TextToSpeech SDK 연동 (답장 음성)
- NaviHelper SDK(경유지 추가), Vehicle SDK HVAC API(온도 조절) 연동
- 동승자 화면 (확인 여부·답장 표시)
- Pleos LLM SDK 공개 시 요약·질문 감지 고도화
