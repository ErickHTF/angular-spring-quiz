CREATE TABLE IF NOT EXISTS quizzes (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS questions (
  id TEXT PRIMARY KEY,
  quiz_id TEXT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  prompt TEXT NOT NULL,
  duration_seconds INTEGER NOT NULL CHECK (duration_seconds BETWEEN 5 AND 120),
  UNIQUE (quiz_id, position)
);

CREATE TABLE IF NOT EXISTS choices (
  id TEXT PRIMARY KEY,
  question_id TEXT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  label TEXT NOT NULL,
  is_correct BOOLEAN NOT NULL DEFAULT false,
  UNIQUE (question_id, position)
);

CREATE TABLE IF NOT EXISTS games (
  id TEXT PRIMARY KEY,
  quiz_id TEXT NOT NULL REFERENCES quizzes(id),
  code CHAR(6) NOT NULL UNIQUE,
  host_token TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('lobby', 'question', 'reveal', 'finished')),
  current_question_position INTEGER NOT NULL DEFAULT 0,
  question_started_at TIMESTAMPTZ,
  question_deadline_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS players (
  id TEXT PRIMARY KEY,
  nickname TEXT NOT NULL,
  player_token TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS game_players (
  game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  score INTEGER NOT NULL DEFAULT 0 CHECK (score >= 0),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (game_id, player_id)
);

CREATE TABLE IF NOT EXISTS answers (
  game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  question_id TEXT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
  player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  choice_id TEXT NOT NULL REFERENCES choices(id),
  is_correct BOOLEAN NOT NULL,
  points INTEGER NOT NULL DEFAULT 0 CHECK (points >= 0),
  answered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (game_id, question_id, player_id)
);

CREATE INDEX IF NOT EXISTS questions_quiz_id_idx ON questions(quiz_id);
CREATE INDEX IF NOT EXISTS choices_question_id_idx ON choices(question_id);
CREATE INDEX IF NOT EXISTS game_players_game_id_idx ON game_players(game_id);
CREATE INDEX IF NOT EXISTS answers_game_question_idx ON answers(game_id, question_id);
