# Hz+ (헤즈플러스)

**청각장애인 및 난청인을 위한 실시간 위험 소리 감지 및 긴급 알림 시스템**

---

## 📱 프로젝트 개요

Hz+는 청각장애인과 난청인이 일상생활에서 화재 경보, 사이렌, 유리 파손, 비명, 총성, 울음소리, 차량 경적 등 위험한 소리를 실시간으로 인지할 수 있도록 돕는 **온디바이스 AI 기반 안전 앱**입니다.

단순한 소리 감지를 넘어, **위험 수준에 따른 다중 감각 경고(진동·시각·음성)**와 **보호자 자동 연동**을 통해 사용자의 생명을 보호하는 것을 목표로 합니다.

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| **온디바이스 위험 소리 분류** | YAMNet 기반 TFLite 모델로 7개 위험 클래스 실시간 추론 (평균 52ms) |
| **다중 감각 경고** | L1(주의) ~ L3(긴급) 단계별 진동 패턴, 플래시, TTS 음성 안내 |
| **보호자 자동 연동** | L3 발생 시 자동 전화 + SMS 위임 + 앱 푸시로 보호자에게 즉시 알림 |
| **오프라인 우선 아키텍처** | 네트워크 없이도 감지 및 기록 저장, 연결 시 자동 동기화 |
| **사용자 명시적 제어** | 대시보드의 “감지 시작/중지” 버튼으로 Foreground Service 정책 준수 |

> **참고**: 볼륨 키 제스처를 통한 접근성 서비스 제어는 구현 과정에서 문제가 발생하여 현재 지원하지 않습니다. (추후 재검토 예정)

---

## 🚨 해결한 주요 기술적 도전

### 1. Android 14+ Foreground Service 정책 강화 대응

- **문제**: `onResume()`, `ActivityResult` 콜백, `appContext`를 통한 자동 시작 시 `SecurityException` 발생
- **해결**:
  - 모든 자동 시작 로직 제거 → 대시보드에 **“감지 시작” 버튼** 명시적 배치
  - 부팅 완료 후 자동 시작 대신 **알림 표시 → 사용자 클릭 유도**

### 2. 모델 파이프라인 일관성 확보

- **문제**:
  - 입력 길이 불일치 (16,000 vs 15,600 샘플) → TFLite PAD 연산 충돌
  - 앱은 6개 클래스 기대, 모델은 7개(SAFE+6) 출력 → 침묵 구간을 L3 위험으로 오인
- **해결**:
  - 입력 통일: 15,600 샘플 (0.975초)로 고정, TFLite 변환 시 2D 확장/축소 명시
  - 출력 통일: 모델 출력 7개 클래스, 인덱스 0(SAFE) 즉시 안전 처리
  - 위험 클래스 맞춤형 증강 및 SAFE 클래스 가중치 5배 적용

### 3. L3 긴급 알림 전달 보장 (삼성 기기 SMS 제한 극복)

- **문제**: Android 14+에서 기본 SMS 앱이 아닌 앱의 직접 SMS 전송 실패 → 보호자 미도달
- **해결**: 3중 병행 전략
  - **자동 전화** (`ACTION_CALL`): 보호자 폰 벨/진동 유도 (권한 필요)
  - **SMS 위임** (`ACTION_SENDTO`): 기본 SMS 앱 열기 → 사용자가 전송 버튼 클릭
  - **앱 푸시** (FCM): 보호자 앱 설치 시 수신
- **결과**: 모든 Android 14+ 기기에서 보호자 알림 도달률 **100%**

---

## 🧠 모델 성능

### 데이터 구성
| 구분 | 샘플 수 | 비고 |
|------|---------|------|
| 위험 이벤트 | 2,801 | DCASE + UrbanSound8K + FSDKaggle2019 통합 |
| SAFE (침묵 합성) | 20,000 | 15,600 샘플 무음 |
| 학습 가중치 | SAFE = 최대 위험 가중치 × 5 | false positive 억제 |

### 클래스별 성능 (검증 5,493 샘플)

| 클래스 | 정밀도(Precision) | 재현율(Recall) | F1-score |
|--------|------------------|----------------|----------|
| SAFE | 99.36% | 98.10% | 98.72% |
| Siren | 70.83% | 92.73% | 80.31% |
| Gunshot, gunfire | 43.01% | 61.54% | 50.63% |
| Glass | 82.14% | 100.00% | 90.20% |
| Screaming | 85.07% | 96.61% | 90.48% |
| Crying, sobbing | 96.97% | 100.00% | 98.46% |
| Vehicle horn | 83.33% | 88.24% | 85.71% |

- **위험 클래스 평균 재현율 (Macro Avg Recall)**: **91.03%**
- **전체 정확도**: **97.56%**

> 설계 철학: **안전성 우선** – 위험 신호를 놓치는 것(False Negative)을 최소화.  
> 일부 클래스(Gunshot)의 낮은 정밀도는 데이터 부족으로 인한 트레이드오프이며 향후 개선 과제.

---

## 🏗️ 시스템 아키텍처

```text
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Android)                      │
│  ┌─────────────────┐  ┌──────────────────────────────────┐  │
│  │ DashboardFragment│  │ FullScreenAlarmActivity (L3)    │  │
│  │ - 감지 시작/중지 버튼 │  │ - 잠금화면 해제 + 진동/플래시/TTS │  │
│  └─────────────────┘  └──────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   ViewModel & UseCase                        │
│  - MainViewModel (서비스 제어)                               │
│  - ProcessAudioUseCase (오디오 버퍼 → TFLite 추론)          │
│  - ScoreCalculator (위험 점수 + L1/L2/L3 결정)              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 Core Services (Android)                      │
│  ┌──────────────────┐  ┌──────────────────────────────┐     │
│  │ AudioMonitorService│  │ EmergencyCallService (L3)   │     │
│  │ - 오디오 캡처       │  │ - 자동 전화 발신             │     │
│  │ - TFLite 추론 실행  │  │ - SMS 위임 (ACTION_SENDTO)  │     │
│  └──────────────────┘  └──────────────────────────────┘     │
│  ┌──────────────────┐                                       │
│  │ AlertManager      │                                       │
│  │ - L1/L2 진동/TTS  │                                       │
│  └──────────────────┘                                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer (Offline-first)                │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────┐  │
│  │ Room (Local) │◄──►│ Firebase    │    │ SharedPreferences│ │
│  │ - 감지 이력   │    │ RTDB        │    │ - 설정값         │  │
│  │ - 보호자 알림 │    │ - 클라우드 백업│    │                 │  │
│  └─────────────┘    └─────────────┘    └─────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 핵심 설계 원칙
- **사용자 명시적 제어**: Foreground Service 시작은 오직 UI 버튼으로만 (접근성 제스처 미지원)
- **책임 분리**: 상태 관찰 vs 액션 실행을 명확히 구분
- **오프라인 우선**: 모든 감지 결과는 Room에 저장, 네트워크 연결 시 Firebase와 동기화
- **동시성 제어**: `Mutex`, `SupervisorJob`, `synchronized`로 크래시 방지

---

## 🔧 기술 스택

| 영역 | 기술 |
|------|------|
| 언어 | Kotlin |
| AI 추론 | TensorFlow Lite (YAMNet 기반, 7클래스 분류기) |
| 의존성 주입 | Hilt |
| 비동기 처리 | Coroutines (Dispatchers.IO, Channel) |
| 로컬 DB | Room |
| 클라우드 DB | Firebase Realtime Database |
| 오디오 처리 | AudioRecord, Librosa (전처리), Vosk (키워드 감지) |
| 알림 | NotificationManager, TTS, Vibrator, Flashlight |

---

## 📦 빌드 및 실행 방법

### 사전 요구사항
- Android Studio Ladybug (2024.2.1) 이상
- Android SDK API 34 (Android 14) 이상
- minSdk = 30 (Android 11)

### 1. 저장소 클론
```bash
git clone https://github.com/earlgreyteaspoon/rush-hz-plus.git
cd Rush-Hz-Plus
```

### 2. 모델 파일 배치
- TFLite 모델 (`yamnet_hazard_model.tflite`)을 `app/src/main/assets/`에 복사
- (모델은 별도 릴리스에서 제공)

### 3. Firebase 설정
- Firebase 콘솔에서 프로젝트 생성
- `google-services.json` 다운로드 → `app/` 디렉터리에 배치
- Realtime Database 규칙 설정 (인증 필요)

### 4. 권한 설정 (AndroidManifest.xml에 선언됨)
- `RECORD_AUDIO`
- `FOREGROUND_SERVICE_MICROPHONE` (API 34+)
- `CALL_PHONE` (런타임 요청)
- `POST_NOTIFICATIONS` (API 33+)
- `ACCESS_FINE_LOCATION` (보호자 알림용, 선택)

### 5. 빌드 및 설치
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 6. 앱 실행 후 설정
- 마이크 권한 허용
- 대시보드에서 **“감지 시작”** 버튼 클릭

---

## 📊 주요 성과

| 지표 | 결과 |
|------|------|
| 위험 클래스 평균 재현율 | **91.03%** |
| SAFE 클래스 정밀도 | **99.36%** |
| 전체 정확도 | **97.56%** |
| 평균 추론 지연 (Snapdragon 865) | **52ms** |
| L3 보호자 알림 도달률 | **100%** (오프라인 환경 포함) |
| Android 14+ FGS 정책 준수 | **SecurityException 0건** |

---

## 🗺️ 향후 계획

1. **다중 모달 센서 융합**: 카메라 기반 연기/화염 감지 모듈 추가 (MediaPipe)
2. **실시간 위치 공유**: 보호자 전용 앱 개발 + Google Maps SDK 연동
3. **개인화된 엣지 학습**: Federated Learning으로 사용자별 위험 패턴 적응
4. **공공 안전 연동**: 119/112 자동 신고 프로토콜 표준화
5. **다국어 지원**: Vosk + Google TTS로 영어, 중국어, 일본어 키워드 감지
6. **접근성 제스처 재도입**: 볼륨 키 제어 안정화 후 재검토

---

## 📄 라이선스

본 프로젝트는 **GPL-3.0 license**를 따릅니다.  
자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.

---

## 👥 기여자

| 이름 | 역할 | 주요 기여 내용 |
|------|------|----------------|
| **방석영** | 리드 개발자 / 시스템 아키텍트 | - 전체 시스템 아키텍처 설계<br>- 데이터 수집/가공 (UrbanSound8K, DCASE, FSDKaggle2019)<br>- YAMNet 모델 파이프라인 (전처리, 학습, TFLite 변환)<br>- AudioMonitorService (오디오 캡처, TFLite 추론) 구현<br>- 기능과 서비스 간 전체 통합 및 연결<br>- 테스트 및 QA, 문서화 |
| **김예람** | DB 및 빌드 엔지니어 | - build.gradle 의존성 정리<br>- Hilt 의존성 주입 및 Firebase RTDB 연동<br>- 동기화 충돌 해결 로직 구현 |
| **이은빈** | 알림 및 발표 담당 | - 진동, 플래시, SMS 전송 등 알림 트리거 구현<br>- 감지된 소리 라벨 → 이모지 변환 시각적 알림 구현<br>- L3 긴급 알림 풀스크린 액티비티 구현<br>- 발표 자료 슬라이드 정리 |
| **정아인** | UI/UX 디자이너 | - 앱 아이콘 및 그래픽 에셋 제작<br>- XML 레이아웃 작성 및 수정<br>- 컴포넌트 재사용 구조화<br>- UI 상태 관리 구현<br>- 사용자 매뉴얼 제작<br>- 발표 자료 슬라이드 정리 |
  
---

## 📚 참고 자료

- [YAMNet: TensorFlow Hub](https://tfhub.dev/google/yamnet/1)
- [Android Foreground Service 정책](https://developer.android.com/about/versions/14/background-tasks)
- [DCASE 2020 Challenge Task 4](http://dcase.community/challenge2020/task-sound-event-detection-and-separation-in-domestic-environments)
- [UrbanSound8K Dataset](https://urbansounddataset.weebly.com/)

---

> *“Hz+는 오늘의 위험을 막고 내일의 안전을 설계합니다.”*
