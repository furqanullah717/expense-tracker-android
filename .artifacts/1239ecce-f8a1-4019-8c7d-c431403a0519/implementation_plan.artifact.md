# 다중 채팅 세션 관리 기능 구현 계획

여러 개의 채팅 세션을 생성하고 관리할 수 있는 기능을 추가합니다. 사이드 메뉴(Navigation Drawer)를 통해 세션 목록을 확인하고 새 채팅을 시작할 수 있습니다.

## 제안된 변경 사항

### 1. 데이터 모델 및 DAO 업데이트
채팅 세션을 관리하기 위한 새로운 엔티티를 추가하고 기존 메시지 엔티티를 세션에 종속되도록 수정합니다.

#### [NEW] [ChatSessionEntity.kt](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/app/src/main/java/com/codewithfk/expensetracker/android/ai/chat_agent/data/ChatSessionEntity.kt)
- `id`: 세션 고유 ID
- `title`: 세션 제목 (첫 번째 메시지 내용으로 자동 설정)
- `lastMessageTime`: 가장 최근 메시지 시간 (정렬용)

#### [MODIFY] [ChatMessageEntity.kt](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/app/src/main/java/com/codewithfk/expensetracker/android/ai/chat_agent/data/ChatMessageEntity.kt)
- `sessionId`: 해당 메시지가 속한 세션의 ID 필드 추가

#### [MODIFY] [ChatDao.kt](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/app/src/main/java/com/codewithfk/expensetracker/android/ai/chat_agent/data/ChatDao.kt)
- 세션 CRUD 메서드 추가 (`getAllSessions`, `insertSession`, `deleteSession` 등)
- 특정 세션의 메시지만 가져오는 쿼리 추가

#### [MODIFY] [ExpenseDatabase.kt](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/app/src/main/java/com/codewithfk/expensetracker/android/data/ExpenseDatabase.kt)
- `ChatSessionEntity` 추가 및 데이터베이스 버전 업그레이드 (Destructive Migration 사용 권장)

### 2. ViewModel 로직 고도화
#### [MODIFY] [AgentViewModel.kt](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/app/src/main/java/com/codewithfk/expensetracker/android/ai/chat_agent/AgentViewModel.kt)
- 현재 활성화된 `currentSessionId` 관리
- 세션 목록 제공 및 세션 전환 기능
- 새 채팅 시작 시 세션 생성 로직 추가

### 3. UI 개선 (Navigation Drawer 도입)
#### [MODIFY] [AgentScreen.kt](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/app/src/main/java/com/codewithfk/expensetracker/android/ai/chat_agent/AgentScreen.kt)
- `ModalNavigationDrawer` 적용
- 상단 바 왼쪽에 메뉴 아이콘 추가
- 사이드 바(DrawerContent)에 채팅 세션 목록 표시
- "새 채팅" 버튼 추가

## 검증 계획
### 수동 테스트
- 사이드 메뉴를 열어 "새 채팅"을 생성할 수 있는지 확인
- 여러 채팅 세션 간 전환이 원활한지 확인
- 채팅 시 해당 세션의 제목이 첫 메시지로 업데이트되는지 확인
- 세션 삭제 기능이 정상 동작하는지 확인
