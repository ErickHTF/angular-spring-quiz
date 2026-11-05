export type GameStatus = 'lobby' | 'question' | 'reveal' | 'finished';

export interface Choice {
  id: string;
  label: string;
  position: number;
}

export interface CurrentQuestion {
  id: string;
  position: number;
  prompt: string;
  durationSeconds: number;
  choices: Choice[];
  correctChoiceId: string | null;
  answerCounts?: Record<string, number>;
}

export interface PlayerSummary {
  id: string;
  nickname: string;
  score: number;
  hasAnswered: boolean;
}

export interface GameState {
  code: string;
  status: GameStatus;
  hostNickname: string | null;
  currentQuestion: CurrentQuestion | null;
  currentQuestionPosition: number;
  totalQuestions: number;
  deadlineAt: string | null;
  players: PlayerSummary[];
}

export interface QuizSummary {
  id: string;
  title: string;
  questionCount: number;
  canEdit?: boolean;
}

export interface ChoiceDraftPayload {
  label: string;
  isCorrect: boolean;
}

export interface QuestionDraftPayload {
  prompt: string;
  durationSeconds: number;
  choices: ChoiceDraftPayload[];
}

export interface QuizDetails {
  id: string;
  title: string;
  questions: QuestionDraftPayload[];
  canEdit: boolean;
}

export interface Me {
  role: 'host' | 'player';
  playerId: string | null;
  nickname: string | null;
}
